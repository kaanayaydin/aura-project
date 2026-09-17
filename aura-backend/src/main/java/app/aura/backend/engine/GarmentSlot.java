package app.aura.backend.engine;

/**
 * CLIP kategorilerinin kombin slotlarina eslenmesi.
 *
 * Vision pipeline aday etiketleri: t-shirt, shirt, pants, jacket, dress,
 * sneakers, perfume bottle, watch, glasses.
 */
public enum GarmentSlot {
    TOP,
    BOTTOM,
    ACCESSORY,
    IGNORED;

    public static GarmentSlot ofCategory(String category) {
        if (category == null || category.isBlank()) {
            return IGNORED;
        }
        return switch (category.trim().toLowerCase()) {
            case "t-shirt", "shirt", "jacket", "dress" -> TOP;
            case "pants" -> BOTTOM;
            case "sneakers", "watch", "glasses" -> ACCESSORY;
            default -> IGNORED; // perfume bottle ve bilinmeyenler
        };
    }
}
