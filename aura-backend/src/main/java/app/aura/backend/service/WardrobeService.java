package app.aura.backend.service;

import app.aura.backend.config.AuraProperties;
import app.aura.backend.dto.CreateWardrobeItemRequest;
import app.aura.backend.dto.WardrobeItemResponse;
import app.aura.backend.model.User;
import app.aura.backend.model.WardrobeItem;
import app.aura.backend.repository.UserRepository;
import app.aura.backend.repository.WardrobeItemRepository;
import app.aura.backend.service.StorageService.Purpose;
import app.aura.backend.support.Base64Images;
import app.aura.backend.web.InvalidImagePayloadException;
import app.aura.backend.web.UserNotFoundException;
import app.aura.backend.web.WardrobeItemNotFoundException;
import app.aura.backend.web.WardrobeOwnershipException;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Sanal dolap is kurallari — v0.20.1: Vision studio normalize + imageUrl.
 */
@Service
public class WardrobeService {

    private static final Logger log = LoggerFactory.getLogger(WardrobeService.class);

    private final WardrobeItemRepository wardrobeItemRepository;
    private final UserRepository userRepository;
    private final AuraProperties properties;
    private final StorageService storageService;
    private final VisionGarmentClient visionGarmentClient;
    private final RestClient downloadClient;

    public WardrobeService(
            WardrobeItemRepository wardrobeItemRepository,
            UserRepository userRepository,
            AuraProperties properties,
            StorageService storageService,
            VisionGarmentClient visionGarmentClient) {
        this.wardrobeItemRepository = wardrobeItemRepository;
        this.userRepository = userRepository;
        this.properties = properties;
        this.storageService = storageService;
        this.visionGarmentClient = visionGarmentClient;
        this.downloadClient = RestClient.create();
    }

    @Transactional
    public WardrobeItemResponse createItem(Long authenticatedUserId, CreateWardrobeItemRequest request) {
        if (!request.hasImageUrl() && !request.hasImageBase64()) {
            throw new InvalidImagePayloadException("imageUrl veya imageBase64 zorunludur.");
        }

        User user = userRepository.findById(authenticatedUserId)
                .orElseThrow(() -> new UserNotFoundException(
                        "Kullanici bulunamadi: %d".formatted(authenticatedUserId)));

        WardrobeItem item = new WardrobeItem(request.category(), null, request.color());
        item.setCategoryConfidence(request.categoryConfidence());
        int imageBytes = 0;

        String originalUrl = null;
        byte[] sourceBytes;
        String sourceMime;

        if (request.hasImageUrl()) {
            originalUrl = request.imageUrl().trim();
            sourceBytes = downloadImageBytes(originalUrl);
            sourceMime = guessMimeFromUrl(originalUrl);
            if (sourceBytes == null || sourceBytes.length == 0) {
                // URL indirilemezse URL'yi oldugu gibi sakla (geriye uyum)
                item.setImageUrl(originalUrl);
                item.setOriginalImageUrl(originalUrl);
                item.setImageMimeType(sourceMime);
                item.setImageBase64(null);
                user.addWardrobeItem(item);
                WardrobeItem saved = wardrobeItemRepository.save(item);
                return WardrobeItemResponse.created(saved, 0);
            }
        } else {
            String normalizedImage = Base64Images.normalize(request.imageBase64());
            sourceBytes = decodeImage(normalizedImage);
            sourceMime = Base64Images.detectMimeType(sourceBytes);
        }

        imageBytes = sourceBytes.length;
        byte[] finalBytes = sourceBytes;
        String finalMime = sourceMime;

        Optional<byte[]> studio = visionGarmentClient.normalizeGarmentPng(
                sourceBytes, request.category() == null ? "garment.png" : request.category() + ".png");
        if (studio.isPresent()) {
            finalBytes = studio.get();
            finalMime = Base64Images.MIME_PNG;
            imageBytes = finalBytes.length;
            log.info(
                    "Wardrobe studio normalize uygulandi: category={} bytes={} mime={}",
                    request.category(),
                    imageBytes,
                    finalMime);
        } else {
            log.info(
                    "Wardrobe studio normalize atlandi/basarisiz: category={} sourceBytes={} mime={}",
                    request.category(),
                    sourceBytes.length,
                    sourceMime);
        }

        if (finalMime == null || finalMime.isBlank()) {
            finalMime = Base64Images.MIME_PNG;
        }

        String objectUrl = storageService.uploadBytes(
                Purpose.WARDROBE, finalBytes, finalMime, request.category());
        objectUrl = rewriteLocalMinioHost(objectUrl);
        item.setImageUrl(objectUrl);
        item.setOriginalImageUrl(originalUrl == null ? null : rewriteLocalMinioHost(originalUrl));
        item.setImageMimeType(finalMime);
        item.setImageBase64(null);

        user.addWardrobeItem(item);
        WardrobeItem saved = wardrobeItemRepository.save(item);
        log.info(
                "Dolaba parca eklendi: id={} kullanici={} kategori={} url={} original={} mime={} bytes={}",
                saved.getId(),
                user.getId(),
                saved.getCategory(),
                saved.getImageUrl(),
                saved.getOriginalImageUrl(),
                saved.getImageMimeType(),
                imageBytes);

        return WardrobeItemResponse.created(saved, imageBytes);
    }

    @Transactional(readOnly = true)
    public List<WardrobeItemResponse> listItems(Long authenticatedUserId, boolean includeImages) {
        return wardrobeItemRepository.findByUserId(authenticatedUserId).stream()
                .map(item -> includeImages
                        ? WardrobeItemResponse.withImage(item)
                        : WardrobeItemResponse.withoutImage(item))
                .toList();
    }

    @Transactional(readOnly = true)
    public WardrobeItemResponse getItem(Long authenticatedUserId, Long itemId) {
        WardrobeItem item = wardrobeItemRepository.findById(itemId)
                .orElseThrow(() -> new WardrobeItemNotFoundException(
                        "Dolap parcasi bulunamadi: %d".formatted(itemId)));
        if (!item.getUser().getId().equals(authenticatedUserId)) {
            throw new WardrobeOwnershipException(
                    "Bu dolap parcasina erisim yetkiniz yok: id=" + itemId);
        }
        return WardrobeItemResponse.withImage(item);
    }

    private byte[] downloadImageBytes(String url) {
        try {
            byte[] body = downloadClient.get()
                    .uri(url)
                    .retrieve()
                    .body(byte[].class);
            if (body != null && body.length > properties.maxImageBytes()) {
                throw new InvalidImagePayloadException(
                        "Gorsel %d byte sinirini asiyor.".formatted(properties.maxImageBytes()));
            }
            return body;
        } catch (RestClientException ex) {
            log.warn("Wardrobe imageUrl indirilemedi ({}): {}", url, ex.toString());
            return null;
        }
    }

    private byte[] decodeImage(String imageBase64) {
        byte[] decoded;
        try {
            decoded = Base64Images.decode(imageBase64);
        } catch (IllegalArgumentException exception) {
            throw new InvalidImagePayloadException("imageBase64 gecerli bir base64 degeri degil.");
        }
        if (decoded.length == 0) {
            throw new InvalidImagePayloadException("imageBase64 bos bir gorsel iceriyor.");
        }
        if (decoded.length > properties.maxImageBytes()) {
            throw new InvalidImagePayloadException(
                    "Gorsel %d byte sinirini asiyor.".formatted(properties.maxImageBytes()));
        }
        return decoded;
    }

    private static String guessMimeFromUrl(String url) {
        String lower = url.toLowerCase();
        if (lower.contains(".jpg") || lower.contains(".jpeg")) {
            return Base64Images.MIME_JPEG;
        }
        if (lower.contains(".webp")) {
            return Base64Images.MIME_WEBP;
        }
        return Base64Images.MIME_PNG;
    }

    /** Docker hostname `minio` / `aura-minio` → istemci erisilebilir 127.0.0.1. */
    static String rewriteLocalMinioHost(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        return url
                .replace("://minio:9000", "://127.0.0.1:9000")
                .replace("://aura-minio:9000", "://127.0.0.1:9000")
                .replace("://localhost:9000", "://127.0.0.1:9000");
    }
}
