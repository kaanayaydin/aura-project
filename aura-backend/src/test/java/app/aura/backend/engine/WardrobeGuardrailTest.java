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

                **navy tişört** ile **siyah pantolon**.
                Yanına **perde** ve bir **masa** ekle.
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
    void dropsSentenceThatMixesInventedBoldWithRealPieces() {
        String raw = "Sabah **lacivert tişört** giy. Öğleden sonra **kırmızı kalem** ile yaz.";

        var result = WardrobeGuardrail.filter(raw, sampleWardrobe(), List.of(), weather);

        assertThat(result.reply()).isEqualTo("Sabah **lacivert tişört** giy.");
        assertThat(result.reply()).doesNotContain("kalem");
        assertThat(result.reply()).doesNotContain("ile yaz");
    }

    @Test
    void replacesSeverelyHallucinatedReplyWithSafeOutfit() {
        String raw = "Perdeyi masa üstüne ser; köpek kolu şart; kalemle tamamla.";

        var result = WardrobeGuardrail.filter(raw, sampleWardrobe(), sampleShelf(), weather);

        assertThat(result.reply()).contains("dolap listesinden");
        assertThat(result.reply()).contains("**lacivert tişört**");
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
        assertThat(result.reply()).contains("**navy tişört** + **siyah pantolon**");
        assertThat(result.droppedLines()).isZero();
        assertThat(result.reply()).doesNotContain(WardrobeGuardrail.EMPTY_SHELF_LINE);
    }

    @Test
    void dropsEmptyPerfumeLabelWhenShelfIsEmpty() {
        String raw = "Koku: **Acqua di Parma — Colonia**.\n**lacivert tişört** + **siyah pantolon**";
        WeatherSnapshot mild = new WeatherSnapshot(
                20.0, 60, "Partly cloudy", 2, 41.0, 29.0, "Istanbul", "open-meteo");

        var result = WardrobeGuardrail.filter(raw, sampleWardrobe(), List.of(), mild);

        assertThat(result.reply()).doesNotContain("Koku: .");
        assertThat(result.reply()).doesNotContain("Acqua");
        assertThat(result.reply()).contains(WardrobeGuardrail.EMPTY_SHELF_LINE);
        assertThat(result.reply().split(java.util.regex.Pattern.quote(WardrobeGuardrail.EMPTY_SHELF_LINE), -1))
                .hasSize(2);
        assertThat(result.reply()).contains("**lacivert tişört** + **siyah pantolon**");
    }

    @Test
    void keepsPlusBetweenAllowedBoldPieces() {
        String raw = "**lacivert tişört** + **siyah pantolon**";

        var result = WardrobeGuardrail.filter(raw, sampleWardrobe(), List.of(), weather);

        assertThat(result.reply()).isEqualTo("**lacivert tişört** + **siyah pantolon**");
    }

    @Test
    void dropsItalicInventedPerfumeWhenShelfIsEmpty() {
        String raw = """
                **lacivert tişört**
                _Aqua di Parma — Colonia_
                """;

        var result = WardrobeGuardrail.filter(raw, sampleWardrobe(), List.of(), weather);

        assertThat(result.reply()).doesNotContain("Aqua");
        assertThat(result.reply()).doesNotContain("Colonia");
        assertThat(result.reply()).contains("**lacivert tişört**");
        assertThat(result.reply()).doesNotContain("dolap listesinden");
    }

    @Test
    void dropsCopiedInventoryHeading() {
        String raw = """
                Dolap -- Kapali Liste:
                **lacivert tişört**
                """;

        var result = WardrobeGuardrail.filter(raw, sampleWardrobe(), List.of(), weather);

        assertThat(result.reply()).doesNotContain("Kapali");
        assertThat(result.reply()).doesNotContain("Liste");
        assertThat(result.reply()).contains("**lacivert tişört**");
    }

    @Test
    void plainKokuSentenceStaysWhenShelfIsEmpty() {
        String raw = "Koku bu havada ağır kalır, hafif bir kombin seç.\n**lacivert tişört**";

        var result = WardrobeGuardrail.filter(raw, sampleWardrobe(), List.of(), weather);

        assertThat(result.reply()).isEqualTo(raw);
        assertThat(result.reply()).doesNotContain(WardrobeGuardrail.EMPTY_SHELF_LINE);
    }

    @Test
    void eveningTemperatureIsNotRewritten() {
        String raw = "Bugün 22°C, akşam 15°C'ye düşebilir.\n**lacivert tişört** + **siyah pantolon**";
        WeatherSnapshot mild = new WeatherSnapshot(
                20.0, 60, "Partly cloudy", 2, 41.0, 29.0, "Istanbul", "open-meteo");

        var result = WardrobeGuardrail.filter(raw, sampleWardrobe(), List.of(), mild);

        assertThat(result.reply()).contains("22°C");
        assertThat(result.reply()).contains("15°C");
        assertThat(result.reply()).doesNotContain("20°C");
    }

    @Test
    void capitalISentenceStays() {
        String raw = "İyi bir gün. İstersen aksesuar ekle.\n**lacivert tişört**";

        var result = WardrobeGuardrail.filter(raw, sampleWardrobe(), List.of(), weather);

        assertThat(result.reply()).isEqualTo(raw);
        assertThat(result.reply()).doesNotContain("\u0307");
    }

    @Test
    void naturalPhrasesAreNotCopiedLabels() {
        String raw = """
                Dolabın bu sezon çok şık duruyor
                Bugünün havası harika geçebilir
                **lacivert tişört**
                """;

        var result = WardrobeGuardrail.filter(raw, sampleWardrobe(), List.of(), weather);

        assertThat(result.reply()).contains("Dolabın bu sezon çok şık duruyor");
        assertThat(result.reply()).contains("Bugünün havası harika geçebilir");
        assertThat(result.reply()).contains("**lacivert tişört**");
    }

    @Test
    void missingGroupSentencesStay() {
        List<WardrobeItem> wardrobe = List.of(
                new WardrobeItem("t-shirt", 0.9, "aGVsbG8=", "image/png", "navy"),
                new WardrobeItem("pants", 0.88, "aGVsbG8=", "image/png", "black"));
        String groups = "Dolabında dış giyim, ayakkabı ve elbise parçaları yok.";
        String one = "Dolabında dış giyim yok.";

        assertThat(WardrobeGuardrail.filter(groups, wardrobe, List.of(), weather).reply())
                .isEqualTo(groups);
        assertThat(WardrobeGuardrail.filter(one, wardrobe, List.of(), weather).reply())
                .isEqualTo(one);
    }

    @Test
    void missingJacketSentenceStaysWhenShirtIsPresent() {
        List<WardrobeItem> wardrobe = List.of(
                new WardrobeItem("shirt", 0.9, "aGVsbG8=", "image/png", "black"),
                new WardrobeItem("pants", 0.88, "aGVsbG8=", "image/png", "black"));
        String raw = "Dolabında ceket yok, bunun yerine gömlekle devam edelim.";

        assertThat(WardrobeGuardrail.filter(raw, wardrobe, List.of(), weather).reply())
                .isEqualTo(raw);
    }

    @Test
    void dropsInventedBoldSentenceWithoutLeavingSuffix() {
        String raw = "Hava uygun. Bugün **kırmızı deri ceket**imi giyebilirsin.";

        var result = WardrobeGuardrail.filter(raw, sampleWardrobe(), List.of(), weather);

        assertThat(result.reply()).isEqualTo("Hava uygun.");
        assertThat(result.reply()).doesNotContain("imi");
        assertThat(result.reply()).doesNotContain("ceket");
    }

    @Test
    void dropsMixedBoldSentenceWithoutLeavingConjunction() {
        String raw = "Hava serin. Öğleden sonra **kırmızı deri ceket** ile **lacivert tişört** kombinini dene.";

        var result = WardrobeGuardrail.filter(raw, sampleWardrobe(), List.of(), weather);

        assertThat(result.reply()).isEqualTo("Hava serin.");
        assertThat(result.reply()).doesNotContain("ile");
        assertThat(result.reply()).doesNotContain("ceket");
    }

    @Test
    void inventedOnlyReplyFallsBackToSafeOutfit() {
        String raw = "Bugün **kırmızı deri ceket**imi giyebilirsin. Yarın **pembe etek** ekle.";

        var result = WardrobeGuardrail.filter(raw, sampleWardrobe(), List.of(), weather);

        assertThat(result.reply()).contains("dolap listesinden");
        assertThat(result.reply()).doesNotContain("imi");
        assertThat(result.reply()).doesNotContain("ceket");
        assertThat(result.reply()).doesNotContain("etek");
    }

    @Test
    void dropsBulletLineThatContainsInventedBold() {
        String raw = """
                - **lacivert tişört**
                - **kırmızı deri ceket** ile **siyah pantolon**
                """;

        var result = WardrobeGuardrail.filter(raw, sampleWardrobe(), List.of(), weather);

        assertThat(result.reply()).contains("- **lacivert tişört**");
        assertThat(result.reply()).doesNotContain("ceket");
        assertThat(result.reply()).doesNotContain(" ile ");
    }

    @Test
    void punctuationOnlyBoldIsNotAClaim() {
        List<WardrobeItem> wardrobe = List.of(
                new WardrobeItem("t-shirt", 0.9, "aGVsbG8=", "image/png", "navy"),
                new WardrobeItem("shirt", 0.9, "aGVsbG8=", "image/png", "black"),
                new WardrobeItem("pants", 0.88, "aGVsbG8=", "image/png", "black"));
        String dash = "**Lacivert tişört** **-** **siyah gömlek**";
        String plus = "**Lacivert tişört** **+** **siyah pantolon**";
        String emdash = "**lacivert tişört** **—** **siyah gömlek**";

        assertThat(WardrobeGuardrail.filter(dash, wardrobe, List.of(), weather).reply()).isEqualTo(dash);
        assertThat(WardrobeGuardrail.filter(plus, wardrobe, List.of(), weather).reply()).isEqualTo(plus);
        assertThat(WardrobeGuardrail.filter(emdash, wardrobe, List.of(), weather).reply()).isEqualTo(emdash);

        String invented = "Bugün **kırmızı deri ceket**imi giyebilirsin.";
        var dropped = WardrobeGuardrail.filter(invented, wardrobe, List.of(), weather);
        assertThat(dropped.reply()).doesNotContain("ceket");
        assertThat(dropped.reply()).doesNotContain("imi");
        assertThat(dropped.reply()).contains("dolap listesinden");

        String mixed = "**Lacivert tişört** **-** **kırmızı ceket**";
        var mixedResult = WardrobeGuardrail.filter(mixed, wardrobe, List.of(), weather);
        assertThat(mixedResult.reply()).doesNotContain("kırmızı");
        assertThat(mixedResult.reply()).doesNotContain("**-**");
        assertThat(mixedResult.reply()).contains("dolap listesinden");
    }

    @Test
    void nonGarmentBoldStays() {
        List<WardrobeItem> wardrobe = List.of(
                new WardrobeItem("t-shirt", 0.9, "aGVsbG8=", "image/png", "navy"),
                new WardrobeItem("shirt", 0.9, "aGVsbG8=", "image/png", "black"),
                new WardrobeItem("pants", 0.88, "aGVsbG8=", "image/png", "black"));
        for (String raw : List.of(
                "Bugün **2** parça yeter, fazlası gereksiz.",
                "**Önemli:** Bugün rüzgar var.",
                "**Sakin** bir gün, sade bir kombin yeter.",
                "**Dikkat:** Akşam serinler.",
                "**Lacivert tişört** ile **siyah pantolon**, **sade** ve **sakin**.")) {
            assertThat(WardrobeGuardrail.filter(raw, wardrobe, List.of(), weather).reply())
                    .as(raw)
                    .isEqualTo(raw);
        }
        assertThat(WardrobeGuardrail.filter("**siyah pantolon**", wardrobe, List.of(), weather).reply())
                .isEqualTo("**siyah pantolon**");
    }

    @Test
    void recognizedMissingGarmentStillDrops() {
        List<WardrobeItem> wardrobe = List.of(
                new WardrobeItem("t-shirt", 0.9, "aGVsbG8=", "image/png", "navy"),
                new WardrobeItem("shirt", 0.9, "aGVsbG8=", "image/png", "black"),
                new WardrobeItem("pants", 0.88, "aGVsbG8=", "image/png", "black"));
        for (String raw : List.of(
                "Bugün **kırmızı deri ceket**imi giyebilirsin.",
                "**Lacivert tişört** **-** **kırmızı ceket**",
                "Boynuna **kravat** tak.",
                "**Perde** ile kombin yap.",
                "**Bere** ve **atkı** tak.",
                // "top" üç harf: ek almaz, tam kelime olarak hâlâ iddiadır.
                "**top** yeter.")) {
            var result = WardrobeGuardrail.filter(raw, wardrobe, List.of(), weather);
            assertThat(result.reply()).as(raw).contains("dolap listesinden");
            assertThat(result.reply()).as(raw).doesNotContain("kravat");
            assertThat(result.reply()).as(raw).doesNotContain("Perde");
            assertThat(result.reply()).as(raw).doesNotContain("ceket");
            assertThat(result.reply()).as(raw).doesNotContain("imi");
        }
    }

    @Test
    void boldGarmentPassesWhenThatCategoryIsInTheWardrobe() {
        List<WardrobeItem> wardrobe = List.of(
                new WardrobeItem("shirt", 0.9, "aGVsbG8=", "image/png", "black"),
                new WardrobeItem("jacket", 0.9, "aGVsbG8=", "image/png", "black"));
        assertThat(WardrobeGuardrail.filter("**ceket**", wardrobe, List.of(), weather).reply())
                .isEqualTo("**ceket**");
        assertThat(WardrobeGuardrail.filter("**ceketler** durur.", wardrobe, List.of(), weather).reply())
                .isEqualTo("**ceketler** durur.");
        assertThat(WardrobeGuardrail.filter("**gömleğin** yakası.", wardrobe, List.of(), weather).reply())
                .isEqualTo("**gömleğin** yakası.");
    }

    @Test
    void innocentBoldWordsStay() {
        // Çıkan kökler: hat, bag, saat, üst, alt, şal/sal, kazak, atlet, tank, ring, tie, cap, giyim.
        // Üç harf ve altı ek almaz; sort ve mont tam kelimedir (sorti, monte).
        List<WardrobeItem> wardrobe = List.of(
                new WardrobeItem("t-shirt", 0.9, "aGVsbG8=", "image/png", "navy"),
                new WardrobeItem("shirt", 0.9, "aGVsbG8=", "image/png", "black"),
                new WardrobeItem("pants", 0.88, "aGVsbG8=", "image/png", "black"));
        List<WardrobeItem> noLayers = List.of(
                new WardrobeItem("dress", 0.9, "aGVsbG8=", "image/png", "black"));
        for (String word : List.of(
                "hata", "hatta", "bağ", "bağlı", "kota", "kotasız", "topu", "topluca",
                "altın", "altı", "üstü", "üstelik", "saat", "saati", "saatlik",
                "botanik", "iç", "dış", "kat", "kol")) {
            String raw = "**" + word + "** cümlede durur.";
            assertThat(WardrobeGuardrail.filter(raw, wardrobe, List.of(), weather).reply())
                    .as(word)
                    .isEqualTo(raw);
        }
        for (List<WardrobeItem> closet : List.of(wardrobe, noLayers)) {
            for (String raw : List.of("**Üst** kısmı hafif tut.", "**Alt** kısmı koyu tut.")) {
                assertThat(WardrobeGuardrail.filter(raw, closet, List.of(), weather).reply())
                        .as(raw)
                        .isEqualTo(raw);
            }
        }
        String clock = "**Saat** 5'te toplantı var; **hafif** bir kombin seç.";
        assertThat(WardrobeGuardrail.filter(clock, wardrobe, List.of(), weather).reply())
                .isEqualTo(clock);
    }

    @Test
    void knownLimitationUnlistedBoldObjectIsKept() {
        String raw = "Üstüne **hayali pelerin** ekle.";
        List<WardrobeItem> wardrobe = List.of(
                new WardrobeItem("t-shirt", 0.9, "aGVsbG8=", "image/png", "navy"));

        assertThat(WardrobeGuardrail.filter(raw, wardrobe, List.of(), weather).reply())
                .isEqualTo(raw);
    }

    @Test
    void keepsPerfumeLineThatNamesTheRealBottle() {
        String raw = "Koku: **Acqua di Parma — Colonia**.";

        var result = WardrobeGuardrail.filter(raw, sampleWardrobe(), sampleShelf(), weather);

        assertThat(result.reply()).isEqualTo(raw);
        assertThat(result.droppedLines()).isZero();
    }
}
