package app.aura.backend.engine;

/**
 * Wardrobe CLIP kategorisini CatVTON / SCHP {@code clothType} degerine esler.
 *
 * <ul>
 *   <li>{@code upper} — ust beden (tişört, ceket, …)</li>
 *   <li>{@code lower} — alt beden (pantolon, etek, …)</li>
 *   <li>{@code overall} — tam boy (elbise, tulum, …)</li>
 * </ul>
 */
public final class ClothTypeMapper {

    public static final String UPPER = "upper";
    public static final String LOWER = "lower";
    public static final String OVERALL = "overall";

    private ClothTypeMapper() {
    }

    /**
     * @param category Vision/CLIP etiketi veya kaba slot adi (TOPS, BOTTOMS, …)
     * @return upper | lower | overall (bilinmeyende upper)
     */
    public static String fromCategory(String category) {
        if (category == null || category.isBlank()) {
            return UPPER;
        }
        String key = category.trim().toLowerCase()
                .replace('_', '-')
                .replace(' ', '-');

        return switch (key) {
            case "pants", "trousers", "jeans", "shorts", "skirt", "bottoms",
                    "bottom", "legging", "leggings" -> LOWER;
            case "dress", "jumpsuit", "jumpsuits", "overall", "romper",
                    "gown", "onesie" -> OVERALL;
            case "t-shirt", "tshirt", "tee", "shirt", "jacket", "coat", "hoodie",
                    "sweater", "blouse", "top", "tops", "blazer", "cardigan",
                    "vest", "polo", "tank", "tank-top", "upper" -> UPPER;
            default -> mapViaGarmentSlot(category);
        };
    }

    private static String mapViaGarmentSlot(String category) {
        return switch (GarmentSlot.ofCategory(category)) {
            case BOTTOM -> LOWER;
            case TOP -> {
                String raw = category.trim().toLowerCase();
                yield raw.contains("dress") ? OVERALL : UPPER;
            }
            case ACCESSORY, IGNORED -> UPPER;
        };
    }
}
