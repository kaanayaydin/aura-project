package app.aura.backend.dto;

import app.aura.backend.engine.ColorHarmony;
import app.aura.backend.engine.PerfumeCatalog.NichePerfume;
import app.aura.backend.engine.PerfumeRuleEngine.PerfumePick;
import java.util.List;

/**
 * Aura oneri cevabi.
 */
public record AuraSuggestionResponse(
        Long userId,
        String vibe,
        String summary,
        ContextSnapshot context,
        SuggestedPiece top,
        SuggestedPiece bottom,
        SuggestedPiece accessory,
        PerfumeRecommendation perfumeRecommendation,
        ColorHarmonyInfo colorHarmony,
        double matchScore,
        List<String> notes) {

    public record ContextSnapshot(
            double temperatureCelsius,
            Double humidityPercent,
            String occasion,
            String seasonBand,
            String weatherSource,
            String weatherCondition,
            String weatherLocation,
            boolean autoWeather) {
    }

    public record SuggestedPiece(
            String role,
            WardrobeItemResponse item,
            double score,
            String reason) {
    }

    public record ColorHarmonyInfo(
            String type,
            double score,
            String explanation) {

        public static ColorHarmonyInfo from(ColorHarmony.Result result) {
            return new ColorHarmonyInfo(
                    result.type().label(),
                    result.score(),
                    result.explanation());
        }
    }

    public record PerfumeRecommendation(
            String id,
            String brand,
            String name,
            String concentration,
            List<String> chords,
            List<String> topNotes,
            List<String> heartNotes,
            List<String> baseNotes,
            String diffusion,
            String blurb,
            double score,
            String reason,
            String thermodynamicNote) {

        public static PerfumeRecommendation from(PerfumePick pick) {
            NichePerfume perfume = pick.perfume();
            return new PerfumeRecommendation(
                    perfume.id(),
                    perfume.brand(),
                    perfume.name(),
                    perfume.concentration(),
                    perfume.chords(),
                    perfume.topNotes(),
                    perfume.heartNotes(),
                    perfume.baseNotes(),
                    perfume.diffusion(),
                    perfume.blurb(),
                    pick.score(),
                    pick.reason(),
                    pick.thermodynamicNote());
        }
    }
}
