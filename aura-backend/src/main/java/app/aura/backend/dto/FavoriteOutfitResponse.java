package app.aura.backend.dto;

import app.aura.backend.model.OutfitFavorite;
import java.time.Instant;

public record FavoriteOutfitResponse(
        Long id,
        Long userId,
        String vibe,
        String summary,
        String occasion,
        Double temperatureCelsius,
        String seasonBand,
        Double matchScore,
        String colorHarmonyType,
        Double colorHarmonyScore,
        Long topItemId,
        Long bottomItemId,
        Long accessoryItemId,
        String perfumeCatalogId,
        String perfumeLabel,
        Instant createdAt) {

    public static FavoriteOutfitResponse from(OutfitFavorite favorite) {
        return new FavoriteOutfitResponse(
                favorite.getId(),
                favorite.getUser().getId(),
                favorite.getVibe(),
                favorite.getSummary(),
                favorite.getOccasion(),
                favorite.getTemperatureCelsius(),
                favorite.getSeasonBand(),
                favorite.getMatchScore(),
                favorite.getColorHarmonyType(),
                favorite.getColorHarmonyScore(),
                favorite.getTopItemId(),
                favorite.getBottomItemId(),
                favorite.getAccessoryItemId(),
                favorite.getPerfumeCatalogId(),
                favorite.getPerfumeLabel(),
                favorite.getCreatedAt());
    }
}
