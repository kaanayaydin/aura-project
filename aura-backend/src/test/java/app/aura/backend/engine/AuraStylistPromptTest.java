package app.aura.backend.engine;

import static org.assertj.core.api.Assertions.assertThat;

import app.aura.backend.dto.WeatherSnapshot;
import app.aura.backend.model.UserPerfume;
import app.aura.backend.model.WardrobeItem;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuraStylistPromptTest {

    @Test
    void describePieceUsesAestheticLanguageWithoutIds() {
        WardrobeItem item = new WardrobeItem("t-shirt", 0.9, "aGVsbG8=", "image/png", "navy");
        assertThat(AuraStylistPrompt.describePiece(item)).isEqualTo("navy tişört");
        assertThat(AuraStylistPrompt.describePiece(item)).doesNotContain("#");
    }

    @Test
    void systemPromptEnforcesClosedInventoryAndPureTurkish() {
        WardrobeItem shirt = new WardrobeItem("shirt", 0.9, "aGVsbG8=", "image/png", "grey");
        UserPerfume perfume = new UserPerfume(
                "adp-colonia",
                "Acqua di Parma",
                "Colonia",
                "EDC",
                "citrus,fresh",
                "bergamot",
                "light");
        WeatherSnapshot weather = new WeatherSnapshot(
                26.0, 55, "Clear", 0, 41.0, 29.0, "Istanbul", "simulated");

        String prompt = AuraStylistPrompt.build(List.of(shirt), List.of(perfume), weather);

        assertThat(prompt).containsIgnoringCase("baş stilistisin");
        assertThat(prompt).containsIgnoringCase("karbon ve şampanya");
        assertThat(prompt).contains("ASLA dolapta olmayan nesneler uydurma");
        assertThat(prompt).contains("Perde");
        assertThat(prompt).containsIgnoringCase("saf Türkçe");
        assertThat(prompt).contains("conditionsine");
        assertThat(prompt).contains("KAPALI LİSTE");
        assertThat(prompt).contains("YANIT BİÇİMİ");
        assertThat(prompt).contains("_Aura notu:");
        assertThat(prompt).contains("26.0°C");
        assertThat(prompt).contains("- gri gömlek");
        assertThat(prompt).contains("Acqua di Parma — Colonia");
        assertThat(prompt).doesNotContain("- #");
    }

    @Test
    void personaForbidsHybridEnglishTurkishAndInventedObjects() {
        String rules = AuraStylistPrompt.personaAndRules() + AuraStylistPrompt.outputContract();
        assertThat(rules).contains("köpek kolu");
        assertThat(rules).contains("absenceindedir");
        assertThat(rules).contains("Doğrudan net, sofistike kombin önerisi");
        assertThat(rules).contains("KÖTÜ ÖRNEK");
    }
}
