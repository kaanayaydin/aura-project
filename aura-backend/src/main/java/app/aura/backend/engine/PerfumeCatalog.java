package app.aura.backend.engine;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * classpath uzerindeki `perfume-catalog.json` kuratorlu niche veri seti.
 */
public final class PerfumeCatalog {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<NichePerfume> PERFUMES = load();

    private PerfumeCatalog() {
    }

    public static List<NichePerfume> all() {
        return PERFUMES;
    }

    private static List<NichePerfume> load() {
        try (InputStream stream = PerfumeCatalog.class
                .getClassLoader()
                .getResourceAsStream("perfume-catalog.json")) {
            if (stream == null) {
                throw new IllegalStateException("perfume-catalog.json classpath'te bulunamadi.");
            }
            CatalogFile file = MAPPER.readValue(stream, CatalogFile.class);
            if (file.perfumes() == null || file.perfumes().isEmpty()) {
                throw new IllegalStateException("perfume-catalog.json bos.");
            }
            return List.copyOf(file.perfumes());
        } catch (IOException exception) {
            throw new IllegalStateException("Parfum katalogu okunamadi.", exception);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CatalogFile(List<NichePerfume> perfumes) {
    }

    /**
     * Kuratorlu tek niche parfum kaydi.
     *
     * @param diffusion light | soft | moderate | strong — hava/nem ile yayilim uyumu
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NichePerfume(
            String id,
            String brand,
            String name,
            String concentration,
            List<String> chords,
            List<String> topNotes,
            List<String> heartNotes,
            List<String> baseNotes,
            String diffusion,
            List<String> bestSeasons,
            List<String> bestOccasions,
            List<String> vibeTags,
            String blurb) {
    }
}
