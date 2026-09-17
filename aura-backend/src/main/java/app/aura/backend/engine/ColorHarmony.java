package app.aura.backend.engine;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Basit renk/stil uyum matrisi.
 *
 * Tipoloji:
 * - monochrome: ayni aile veya ayni ton
 * - analogous: uyumlu tonlar (notr + her sey, ayni sicaklik bandi)
 * - contrast: sicak/soguk veya koyu/acik zıtligi
 * - neutral: en az bir parca renksiz/bilinmiyor
 */
public final class ColorHarmony {

    public enum Family {
        NEUTRAL,
        WARM,
        COOL,
        UNKNOWN
    }

    public enum Type {
        MONOCHROME,
        ANALOGOUS,
        CONTRAST,
        NEUTRAL,
        UNKNOWN;

        public String label() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public record Result(Type type, double score, String explanation) {
    }

    private static final Set<String> NEUTRALS = Set.of(
            "black", "white", "grey", "gray", "beige", "cream", "ivory",
            "navy", "khaki", "taupe", "charcoal", "silver", "gold",
            "siyah", "beyaz", "gri", "bej", "krem");

    private static final Set<String> WARMS = Set.of(
            "red", "orange", "yellow", "coral", "burgundy", "brown", "camel",
            "terracotta", "maroon", "rust", "mustard", "pink",
            "kirmizi", "turuncu", "sari", "kahve", "pembe");

    private static final Set<String> COOLS = Set.of(
            "blue", "green", "teal", "purple", "violet", "mint", "turquoise",
            "cyan", "lavender", "olive",
            "mavi", "yesil", "mor", "turkuaz");

    private ColorHarmony() {
    }

    public static Family familyOf(String color) {
        if (color == null || color.isBlank()) {
            return Family.UNKNOWN;
        }
        String normalized = color.trim().toLowerCase(Locale.ROOT);
        if (NEUTRALS.contains(normalized) || containsAny(normalized, NEUTRALS)) {
            return Family.NEUTRAL;
        }
        if (WARMS.contains(normalized) || containsAny(normalized, WARMS)) {
            return Family.WARM;
        }
        if (COOLS.contains(normalized) || containsAny(normalized, COOLS)) {
            return Family.COOL;
        }
        return Family.UNKNOWN;
    }

    /**
     * Iki renk arasindaki uyum skoru (0-1) ve tip.
     */
    public static Result scorePair(String colorA, String colorB) {
        Family a = familyOf(colorA);
        Family b = familyOf(colorB);

        if (a == Family.UNKNOWN && b == Family.UNKNOWN) {
            return new Result(Type.UNKNOWN, 0.55, "Renk bilgisi yok; notr varsayildi.");
        }
        if (a == Family.UNKNOWN || b == Family.UNKNOWN) {
            return new Result(Type.NEUTRAL, 0.70, "Eksik renk notr kabul edilerek uyumlu sayildi.");
        }
        if (a == Family.NEUTRAL || b == Family.NEUTRAL) {
            boolean same = sameTone(colorA, colorB);
            if (same) {
                return new Result(Type.MONOCHROME, 0.95, "Notr monokrom eslesme.");
            }
            return new Result(Type.ANALOGOUS, 0.88, "Notr ton her renkle uyumlu.");
        }
        if (a == b) {
            if (sameTone(colorA, colorB)) {
                return new Result(Type.MONOCHROME, 0.96, "Ayni ton monokrom kombin.");
            }
            return new Result(Type.ANALOGOUS, 0.90, "Ayni sicaklik bandinda uyumlu tonlar.");
        }
        // warm vs cool
        return new Result(Type.CONTRAST, 0.78, "Sicak/soguk kontrast; bilincli zıtlik.");
    }

    /**
     * Ust / alt / aksesuar ucgeninin birlesik uyumu.
     */
    public static Result scoreOutfit(
            Optional<String> topColor,
            Optional<String> bottomColor,
            Optional<String> accessoryColor) {
        double sum = 0;
        int count = 0;
        Type bestType = Type.UNKNOWN;
        double bestScore = -1;
        String explanation = "Renk verisi yetersiz.";

        if (topColor.isPresent() && bottomColor.isPresent()) {
            Result pair = scorePair(topColor.get(), bottomColor.get());
            sum += pair.score();
            count++;
            if (pair.score() > bestScore) {
                bestScore = pair.score();
                bestType = pair.type();
                explanation = "Ust-alt: " + pair.explanation();
            }
        }
        if (topColor.isPresent() && accessoryColor.isPresent()) {
            Result pair = scorePair(topColor.get(), accessoryColor.get());
            sum += pair.score() * 0.85;
            count++;
            if (pair.score() > bestScore) {
                bestScore = pair.score();
                bestType = pair.type();
                explanation = "Ust-aksesuar: " + pair.explanation();
            }
        }
        if (bottomColor.isPresent() && accessoryColor.isPresent()) {
            Result pair = scorePair(bottomColor.get(), accessoryColor.get());
            sum += pair.score() * 0.75;
            count++;
        }

        if (count == 0) {
            return new Result(Type.UNKNOWN, 0.55, explanation);
        }
        double avg = sum / count;
        return new Result(bestType, round2(avg), explanation);
    }

    private static boolean sameTone(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return a.trim().equalsIgnoreCase(b.trim());
    }

    private static boolean containsAny(String value, Set<String> tokens) {
        for (String token : tokens) {
            if (value.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
