package app.aura.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import app.aura.backend.config.ChatProperties;
import app.aura.backend.dto.WeatherSnapshot;
import app.aura.backend.model.WardrobeItem;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Yedek kombin satırı OutfitRuleEngine'den gelir. Hava sıcaklığı burada sabitlenir;
 * denetleyici testi canlı hava döndürdüğü için bu farkı gösteremez.
 */
class AuraChatServiceFallbackTest {

    private final AuraChatService service = new AuraChatService(
            new ChatProperties("http://127.0.0.1:9", "test", 5, 0.35, true, 8192),
            null,
            null,
            null,
            null);

    @Test
    void coldFallbackPicksJacketNotTheFirstTshirt() {
        String reply = service.buildFallbackReply(
                "Bugün ne giysem?",
                seasonalWardrobe(),
                List.of(),
                weather(5.0));

        assertThat(reply).isEqualTo("""
                Bugün hava 5°C, Açık.

                Kombin önerim: **siyah ceket** + **lacivert pantolon**.
                Hava serin; kat kat giyin.
                Parfüm rafın boş; istersen bir şişe ekleyebilirsin.

                _Aura notu: az parça, net çizgi._
                """.strip());
        assertThat(reply).doesNotContain("tişört");
        assertNoEngineLeak(reply);
    }

    @Test
    void hotFallbackPicksTshirtNotJacket() {
        String reply = service.buildFallbackReply(
                "Bugün ne giysem?",
                seasonalWardrobe(),
                List.of(),
                weather(30.0));

        assertThat(reply).isEqualTo("""
                Bugün hava 30°C, Açık.

                Kombin önerim: **beyaz tişört** + **lacivert pantolon**.
                Hava sıcak; ince ve hafif parçalarla kal.
                Parfüm rafın boş; istersen bir şişe ekleyebilirsin.

                _Aura notu: az parça, net çizgi._
                """.strip());
        assertThat(reply).doesNotContain("ceket");
        assertNoEngineLeak(reply);
    }

    @Test
    void ignoredOnlyWardrobeStillProducesReply() {
        WardrobeItem bottle = new WardrobeItem(
                "perfume bottle", 0.9, "x", "image/png", null);

        assertThatCode(() -> service.buildFallbackReply(
                        "Bugün ne giysem?",
                        List.of(bottle),
                        List.of(),
                        weather(18.0)))
                .doesNotThrowAnyException();

        String reply = service.buildFallbackReply(
                "Bugün ne giysem?",
                List.of(bottle),
                List.of(),
                weather(18.0));

        assertThat(reply).isNotBlank().contains("Kombin önerim:");
        assertNoEngineLeak(reply);
    }

    /** Eski davranış listenin ilk üstünü alırdı: beyaz tişört, sonra ceket. */
    private static List<WardrobeItem> seasonalWardrobe() {
        return List.of(
                new WardrobeItem("t-shirt", 0.9, "x", "image/png", "white"),
                new WardrobeItem("jacket", 0.9, "x", "image/png", "black"),
                new WardrobeItem("pants", 0.9, "x", "image/png", "navy"));
    }

    private static WeatherSnapshot weather(double celsius) {
        return new WeatherSnapshot(celsius, 40.0, "Clear", 0, 41.0, 29.0, null, "test");
    }

    private static void assertNoEngineLeak(String reply) {
        assertThat(reply)
                .doesNotContain("%")
                .doesNotContain("matchScore")
                .doesNotContain("Renk uyumu:");
    }
}
