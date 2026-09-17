package app.aura.backend.dto;

import app.aura.backend.model.WardrobeItem;
import app.aura.backend.support.Base64Images;

/**
 * Dolap parcasinin cevap temsili.
 *
 * v0.20+: {@code imageUrl} birincil (stüdyo normalize); base64 geriye uyumluluk.
 */
public record WardrobeItemResponse(
        Long id,
        Long userId,
        String category,
        Double categoryConfidence,
        String color,
        int imageBytes,
        String imageMimeType,
        String imageUrl,
        String originalImageUrl,
        String imageBase64,
        String imageDataUri) {

    public static WardrobeItemResponse withImage(WardrobeItem item) {
        String base64 = Base64Images.normalize(item.getImageBase64());
        String mimeType = resolveMimeType(item, base64);
        return new WardrobeItemResponse(
                item.getId(),
                item.getUser().getId(),
                item.getCategory(),
                item.getCategoryConfidence(),
                item.getColor(),
                byteLength(base64),
                mimeType,
                item.getImageUrl(),
                item.getOriginalImageUrl(),
                base64,
                base64 == null ? null : Base64Images.toDataUri(base64, mimeType));
    }

    public static WardrobeItemResponse withoutImage(WardrobeItem item) {
        String base64 = Base64Images.normalize(item.getImageBase64());
        return new WardrobeItemResponse(
                item.getId(),
                item.getUser().getId(),
                item.getCategory(),
                item.getCategoryConfidence(),
                item.getColor(),
                byteLength(base64),
                resolveMimeType(item, base64),
                item.getImageUrl(),
                item.getOriginalImageUrl(),
                null,
                null);
    }

    public static WardrobeItemResponse created(WardrobeItem item, int imageBytes) {
        return new WardrobeItemResponse(
                item.getId(),
                item.getUser().getId(),
                item.getCategory(),
                item.getCategoryConfidence(),
                item.getColor(),
                imageBytes,
                item.getImageMimeType(),
                item.getImageUrl(),
                item.getOriginalImageUrl(),
                null,
                null);
    }

    private static String resolveMimeType(WardrobeItem item, String base64) {
        if (item.getImageMimeType() != null && !item.getImageMimeType().isBlank()) {
            return item.getImageMimeType();
        }
        if (base64 == null || base64.isEmpty()) {
            return null;
        }
        try {
            return Base64Images.detectMimeType(Base64Images.decode(base64));
        } catch (IllegalArgumentException exception) {
            return Base64Images.MIME_UNKNOWN;
        }
    }

    private static int byteLength(String base64) {
        if (base64 == null || base64.isEmpty()) {
            return 0;
        }
        try {
            return Base64Images.decode(base64).length;
        } catch (IllegalArgumentException exception) {
            return 0;
        }
    }
}
