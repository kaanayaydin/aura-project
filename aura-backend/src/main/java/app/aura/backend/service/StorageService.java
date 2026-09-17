package app.aura.backend.service;

import app.aura.backend.config.StorageProperties;
import app.aura.backend.service.storage.ObjectStorage;
import app.aura.backend.service.storage.ObjectStorage.PresignedUpload;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Presigned upload / public URL / object silme.
 */
@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    public enum Purpose {
        WARDROBE,
        VTON_PERSON,
        VTON_RESULT,
        AVATAR
    }

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/png", "image/jpeg", "image/jpg", "image/webp");

    private final ObjectStorage objectStorage;
    private final StorageProperties properties;

    public StorageService(ObjectStorage objectStorage, StorageProperties properties) {
        this.objectStorage = objectStorage;
        this.properties = properties;
    }

    public PresignedUpload generatePresignedUploadUrl(
            Purpose purpose, String contentType, String filenameHint) {
        String mime = normalizeContentType(contentType);
        String bucket = bucketFor(purpose);
        String key = buildKey(purpose, filenameHint, mime);
        Duration ttl = Duration.ofSeconds(properties.uploadTtlSeconds());
        return objectStorage.generatePresignedUploadUrl(bucket, key, mime, ttl);
    }

    public String generatePublicUrl(String bucket, String key) {
        return objectStorage.generatePublicUrl(bucket, key);
    }

    public String uploadBytes(Purpose purpose, byte[] bytes, String contentType, String filenameHint) {
        String mime = normalizeContentType(contentType);
        String bucket = bucketFor(purpose);
        String key = buildKey(purpose, filenameHint, mime);
        objectStorage.putObject(bucket, key, bytes, mime);
        String url = objectStorage.generatePublicUrl(bucket, key);
        log.info(
                "Object upload: purpose={} bucket={} key={} mime={} bytes={} url={}",
                purpose,
                bucket,
                key,
                mime,
                bytes == null ? 0 : bytes.length,
                url);
        return url;
    }

    public void deleteObject(String bucket, String key) {
        objectStorage.deleteObject(bucket, key);
    }

    public StorageProperties properties() {
        return properties;
    }

    private String bucketFor(Purpose purpose) {
        return switch (purpose) {
            case WARDROBE -> properties.wardrobeBucket();
            case VTON_PERSON, VTON_RESULT -> properties.vtonBucket();
            case AVATAR -> properties.avatarsBucket();
        };
    }

    private static String buildKey(Purpose purpose, String filenameHint, String contentType) {
        String ext = extensionFor(contentType);
        String safe = sanitize(filenameHint);
        String id = UUID.randomUUID().toString().replace("-", "");
        String folder = switch (purpose) {
            case WARDROBE -> "items";
            case VTON_PERSON -> "person";
            case VTON_RESULT -> "results";
            case AVATAR -> "avatars";
        };
        if (safe == null || safe.isBlank()) {
            return folder + "/" + id + ext;
        }
        return folder + "/" + id + "-" + safe + ext;
    }

    private static String sanitize(String filenameHint) {
        if (filenameHint == null || filenameHint.isBlank()) {
            return null;
        }
        String base = filenameHint;
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        return base.replaceAll("[^a-zA-Z0-9_-]", "_").toLowerCase(Locale.ROOT);
    }

    private static String normalizeContentType(String contentType) {
        String mime = contentType == null ? "image/png" : contentType.trim().toLowerCase(Locale.ROOT);
        if ("image/jpg".equals(mime)) {
            mime = "image/jpeg";
        }
        if (!ALLOWED_TYPES.contains(mime) && !"image/jpeg".equals(mime)) {
            if (!mime.startsWith("image/")) {
                throw new IllegalArgumentException("Desteklenmeyen contentType: " + contentType);
            }
        }
        return mime;
    }

    private static String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/webp" -> ".webp";
            default -> ".png";
        };
    }
}
