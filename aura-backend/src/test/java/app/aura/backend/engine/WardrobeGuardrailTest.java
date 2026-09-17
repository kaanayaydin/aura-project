package app.aura.backend.engine;

import static org.assertj.core.api.Assertions.assertThat;

import app.aura.backend.dto.WeatherSnapshot;
import app.aura.backend.model.UserPerfume;
import app.aura.backend.model.WardrobeItem;
import java.util.List;
import org.junit.jupiter.api.Test;

class WardrobeGuardrailTest {

    private final WeatherSnapshot weather = new WeatherSnapshot(
            26.0, 55, "Clear", 0, 41.0, 29.0, "Istanbul", "simulated");

    private List<WardrobeItem> sampleWardrobe() {
        return List.of(
                new WardrobeItem("t-shirt", 0.9, "aGVsbG8=", "image/png", "navy"),
                new WardrobeItem("pants", 0.88, "aGVsbG8=", "image/png", "black"));
    }

    private List<UserPerfume> sampleShelf() {
        return List.of(new UserPerfume(
                "adp-colonia",
                "Acqua di Parma",
                "Colonia",
                "EDC",
                "citrus,fresh",
                "bergamot",
                "light"));
    }

    @Test
    void stripsForbiddenObjectsLikeCurtainAndTable() {
        String raw = """
                Bugün sahne **26°C**.

                **navy tişört** ile **siyah pantolon**; yanına **perde** ve bir **masa** ekle.
                Koku: **Acqua di Parma — Colonia**.

                _Aura notu: sessiz güç._
                """;

        var result = WardrobeGuardrail.filter(raw, sampleWardrobe(), sampleShelf(), weather);

        assertThat(result.reply()).doesNotContainIgnoringCase("perde");
        assertThat(result.reply()).doesNotContainIgnoringCase("masa");
        assertThat(result.reply()).containsIgnoringCase("tişört");
        assertThat(result.mutated()).isTrue();
    }

    @Test
    void dropsEntireLineWhenOnlyHallucinatedFurniture() {
        String raw = """
                Bugün ferah bir gün.

                Odaya bir perde ve sandalye koy.

                **navy tişört** giy.
                """;

        var result = WardrobeGuardrail.filter(raw, sampleWardrobe(), List.of(), weather);

        assertThat(result.reply()).doesNotContain("perde");
        assertThat(result.reply()).doesNotContain("sandalye");
        assertThat(result.droppedLines()).isGreaterThanOrEqualTo(1);
        assertThat(result.reply()).containsIgnoringCase("tişört");
    }

    @Test
    void removesBoldClaimNotInWardrobe() {
        String raw = "Kombin: **navy tişört** + **kırmızı kalem** + **siyah pantolon**.";

        var result = WardrobeGuardrail.filter(raw, sampleWardrobe(), List.of(), weather);

        assertThat(result.reply()).contains("**navy tişört**");
        assertThat(result.reply()).contains("**siyah pantolon**");
        assertThat(result.reply()).doesNotContain("kalem");
    }

    @Test
    void replacesSeverelyHallucinatedReplyWithSafeOutfit() {
        String raw = "Perdeyi masa üstüne ser; köpek kolu şart; kalemle tamamla.";

        var result = WardrobeGuardrail.filter(raw, sampleWardrobe(), sampleShelf(), weather);

        assertThat(result.reply()).contains("dolap listesinden");
        assertThat(result.reply()).contains("**navy tişört**");
        assertThat(result.reply()).contains("Acqua di Parma");
        assertThat(result.reply()).doesNotContain("perde");
        assertThat(result.reply()).doesNotContain("masa");
        assertThat(result.mutated()).isTrue();
    }

    @Test
    void keepsCleanInventoryBoundReply() {
        String raw = """
                Bugün sahne **26°C**.

                Kombin: **navy tişört** + **siyah pantolon**.
                Koku: **Acqua di Parma — Colonia**.

                _Aura notu: az parça, net çizgi._
                """;

        var result = WardrobeGuardrail.filter(raw, sampleWardrobe(), sampleShelf(), weather);

        assertThat(result.reply()).contains("**navy tişört**");
        assertThat(result.reply()).contains("**siyah pantolon**");
        assertThat(result.reply()).contains("Colonia");
        assertThat(result.droppedLines()).isZero();
    }
}
