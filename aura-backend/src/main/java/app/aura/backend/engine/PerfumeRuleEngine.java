package app.aura.backend.engine;

import app.aura.backend.engine.PerfumeCatalog.NichePerfume;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Termodinamik koku motoru.
 *
 * Sicak/nemli havada hafif-ferah (citrus/aquatic/green) profilleri onerir;
 * sogukta odunsu/baharatli/oriental yayilim artar. Occasion ve kombin vibe'i
 * skorun geri kalanini belirler.
 */
public final class PerfumeRuleEngine {

    public record PerfumePick(
            NichePerfume perfume,
            double score,
            String reason,
            String thermodynamicNote) {
    }

    public PerfumePick recommend(
            SeasonBand season,
            Occasion occasion,
            Double humidityPercent,
            String vibe) {
        List<PerfumePick> scored = PerfumeCatalog.all().stream()
                .map(perfume -> score(perfume, season, occasion, humidityPercent, vibe))
                .sorted(Comparator.comparingDouble(PerfumePick::score).reversed())
                .toList();

        if (scored.isEmpty()) {
            throw new IllegalStateException("Parfum katalogu bos; oneri uretilemiyor.");
        }
        return scored.getFirst();
    }

    PerfumePick score(
            NichePerfume perfume,
            SeasonBand season,
            Occasion occasion,
            Double humidityPercent,
            String vibe) {
        double seasonScore = membership(perfume.bestSeasons(), season.label());
        double occasionScore = membership(perfume.bestOccasions(), occasion.label());
        double vibeScore = vibeOverlap(perfume.vibeTags(), vibe);
        double thermoScore = thermodynamicFit(perfume, season, humidityPercent);

        double total = seasonScore * 0.35
                + occasionScore * 0.30
                + vibeScore * 0.20
                + thermoScore * 0.15;

        String reason = buildReason(perfume, season, occasion, seasonScore, occasionScore, thermoScore);
        String thermoNote = thermodynamicNarrative(perfume, season, humidityPercent);
        return new PerfumePick(perfume, round2(total), reason, thermoNote);
    }

    private double thermodynamicFit(
            NichePerfume perfume,
            SeasonBand season,
            Double humidityPercent) {
        String diffusion = safe(perfume.diffusion());
        boolean humid = humidityPercent != null && humidityPercent >= 70.0;
        Set<String> chords = perfume.chords().stream()
                .map(c -> c.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        double score = 0.55;

        // Sicak: hafif + ferah akorlar; agir oriental cezalandirilir
        if (season == SeasonBand.HOT) {
            if (Set.of("light", "soft").contains(diffusion)) {
                score += 0.30;
            } else if ("strong".equals(diffusion)) {
                score -= 0.35;
            }
            if (intersects(chords, "citrus", "fresh", "aquatic", "green")) {
                score += 0.25;
            }
            if (intersects(chords, "oriental", "spicy")) {
                score -= 0.20;
            }
            if (humid && intersects(chords, "oriental", "woody") && "strong".equals(diffusion)) {
                score -= 0.15; // nemde agir molekuller bastirici olur
            }
        }

        // Mild: dengeli
        if (season == SeasonBand.MILD) {
            if (Set.of("light", "moderate", "soft").contains(diffusion)) {
                score += 0.20;
            }
            if (intersects(chords, "woody", "aromatic", "citrus", "fresh")) {
                score += 0.15;
            }
        }

        // Cool/Cold: daha yogun yayilim + odunsu/baharat
        if (season == SeasonBand.COOL || season == SeasonBand.COLD) {
            if (Set.of("moderate", "strong").contains(diffusion)) {
                score += 0.25;
            } else if ("light".equals(diffusion)) {
                score -= 0.15; // soguk hava hafif notalari hizla dagitir
            }
            if (intersects(chords, "woody", "spicy", "oriental")) {
                score += 0.25;
            }
            if (intersects(chords, "aquatic", "citrus") && season == SeasonBand.COLD) {
                score -= 0.10;
            }
        }

        return clamp(score);
    }

    private double vibeOverlap(List<String> tags, String vibe) {
        if (vibe == null || vibe.isBlank() || tags == null || tags.isEmpty()) {
            return 0.45;
        }
        String normalizedVibe = vibe.toLowerCase(Locale.ROOT);
        long hits = tags.stream()
                .filter(tag -> normalizedVibe.contains(tag.toLowerCase(Locale.ROOT)))
                .count();
        if (hits == 0) {
            return 0.30;
        }
        return clamp(0.45 + hits * 0.25);
    }

    private static double membership(List<String> haystack, String needle) {
        if (haystack == null || haystack.isEmpty()) {
            return 0.40;
        }
        boolean hit = haystack.stream().anyMatch(value -> value.equalsIgnoreCase(needle));
        return hit ? 1.0 : 0.25;
    }

    private static boolean intersects(Set<String> chords, String... wanted) {
        for (String chord : wanted) {
            if (chords.contains(chord)) {
                return true;
            }
        }
        return false;
    }

    private String buildReason(
            NichePerfume perfume,
            SeasonBand season,
            Occasion occasion,
            double seasonScore,
            double occasionScore,
            double thermoScore) {
        List<String> parts = new ArrayList<>();
        parts.add(seasonScore >= 0.9
                ? season.label() + " mevsimine birebir"
                : season.label() + " icin kabul edilebilir");
        parts.add(occasionScore >= 0.9
                ? occasion.label() + " baglamina guclu uyum"
                : occasion.label() + " icin orta uyum");
        parts.add(thermoScore >= 0.7
                ? "yayilim (" + perfume.diffusion() + ") termodinamik olarak uygun"
                : "yayilim (" + perfume.diffusion() + ") kisitli uyum");
        return String.join("; ", parts);
    }

    private String thermodynamicNarrative(
            NichePerfume perfume,
            SeasonBand season,
            Double humidityPercent) {
        boolean humid = humidityPercent != null && humidityPercent >= 70.0;
        return switch (season) {
            case HOT -> humid
                    ? "Yuksek nemde ucucu ust notalar (citrus/yesil) daha temiz algilanir; "
                            + perfume.diffusion() + " yayilim tercih edildi."
                    : "Sicak havada hafif molekuller cildi sardirmadan ferahlik verir.";
            case MILD -> "Ilik havada orta projeksiyon dengeli bir aura birakir.";
            case COOL -> "Serin hava dip notalari (odun/baharat) yavasca acar.";
            case COLD -> "Sogukta difuzyon yavaslar; daha yogun/" + perfume.diffusion()
                    + " profil sicaklik hissi tamamlar.";
        };
    }

    private static String safe(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
