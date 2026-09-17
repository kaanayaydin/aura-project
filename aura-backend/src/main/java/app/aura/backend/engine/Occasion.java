package app.aura.backend.engine;

import java.util.Locale;
import java.util.Optional;

/**
 * Desteklenen takvim baglamlari. Bilinmeyen degerler CASUAL'a dusmez;
 * cagiran katman dogrulama hatasi uretir.
 */
public enum Occasion {
    MEETING,
    CASUAL,
    SPORT;

    public static Optional<Occasion> tryParse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "meeting", "work", "office", "formal" -> Optional.of(MEETING);
            case "casual", "daily", "weekend" -> Optional.of(CASUAL);
            case "sport", "gym", "workout", "athletic" -> Optional.of(SPORT);
            default -> Optional.empty();
        };
    }

    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }
}
