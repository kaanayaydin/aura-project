package app.aura.backend.dto;

import app.aura.backend.engine.PerfumeCatalog.NichePerfume;
import app.aura.backend.model.UserPerfume;
import java.util.Arrays;
import java.util.List;

/**
 * Kullanici parfum rafi kaydi veya katalog gorunumu.
 */
public record UserPerfumeResponse(
        Long id,
        Long userId,
        String catalogId,
        String brand,
        String name,
        String concentration,
        List<String> chords,
        String notes,
        String diffusion,
        List<String> topNotes,
        List<String> heartNotes,
        List<String> baseNotes,
        String blurb,
        boolean onShelf) {

    public static UserPerfumeResponse fromShelf(UserPerfume perfume) {
        return new UserPerfumeResponse(
                perfume.getId(),
                perfume.getUser().getId(),
                perfume.getCatalogId(),
                perfume.getBrand(),
                perfume.getName(),
                perfume.getConcentration(),
                split(perfume.getChords()),
                perfume.getNotes(),
                perfume.getDiffusion(),
                List.of(),
                List.of(),
                List.of(),
                null,
                true);
    }

    public static UserPerfumeResponse fromCatalog(NichePerfume perfume, boolean onShelf) {
        return new UserPerfumeResponse(
                null,
                null,
                perfume.id(),
                perfume.brand(),
                perfume.name(),
                perfume.concentration(),
                perfume.chords(),
                joinNotes(perfume),
                perfume.diffusion(),
                perfume.topNotes(),
                perfume.heartNotes(),
                perfume.baseNotes(),
                perfume.blurb(),
                onShelf);
    }

    private static List<String> split(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static String joinNotes(NichePerfume perfume) {
        return String.join(
                " | ",
                "ust: " + String.join(", ", perfume.topNotes()),
                "kalp: " + String.join(", ", perfume.heartNotes()),
                "dip: " + String.join(", ", perfume.baseNotes()));
    }
}
