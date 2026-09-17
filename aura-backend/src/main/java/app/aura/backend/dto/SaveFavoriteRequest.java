package app.aura.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Favori kombin kaydetme istegi — kimlik JWT Principal'dan gelir.
 * {@code userId} alanı opsiyonel geriye uyumluluk içindir ve yok sayılır.
 */
public record SaveFavoriteRequest(
        Long userId,

        @Size(max = 120)
        String vibe,

        String summary,

        @NotBlank(message = "occasion zorunludur")
        @Size(max = 40)
        String occasion,

        Double temperatureCelsius,

        @Size(max = 20)
        String seasonBand,

        Double matchScore,

        @Size(max = 40)
        String colorHarmonyType,

        Double colorHarmonyScore,

        Long topItemId,
        Long bottomItemId,
        Long accessoryItemId,

        @Size(max = 80)
        String perfumeCatalogId,

        @Size(max = 200)
        String perfumeLabel) {
}
