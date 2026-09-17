package app.aura.backend.engine;

import app.aura.backend.dto.WeatherSnapshot;
import app.aura.backend.model.UserPerfume;
import app.aura.backend.model.WardrobeItem;
import java.util.List;
import java.util.Locale;

/**
 * Aura bas stilist system prompt — luks moda editoru + anti-halusinasyon kurallari.
 *
 * Envanter kapali listedir; LLM yalnizca listedeki gercek parcacari kullanir.
 */
public final class AuraStylistPrompt {

    private static final int MAX_WARDROBE_LINES = 40;
    private static final int MAX_PERFUME_LINES = 20;

    private AuraStylistPrompt() {
    }

    public static String build(
            List<WardrobeItem> wardrobe,
            List<UserPerfume> shelf,
            WeatherSnapshot weather) {
        StringBuilder sb = new StringBuilder();
        sb.append(personaAndRules());
        sb.append('\n');
        sb.append(weatherBlock(weather));
        sb.append('\n');
        sb.append(wardrobeBlock(wardrobe));
        sb.append('\n');
        sb.append(perfumeBlock(shelf));
        sb.append('\n');
        sb.append(outputContract());
        return sb.toString();
    }

    static String personaAndRules() {
        return """
                Sen Aura'nın baş stilistisin. Karbon ve şampanya estetiğini benimsemiş, \
                sofistike, kibar, nokta atışı öneriler yapan lüks bir moda danışmanısın.

                === SERT KURALLAR (İHLAL YASAK) ===

                ENVANTER — KAPALI LİSTE:
                - ASLA dolapta olmayan nesneler uydurma. Perde, kalem, köpek kolu, masa, sandalye, \
                çiçek, duvar, perde gibi kıyafet dışı veya listede olmayan hiçbir şey önerme.
                - Yalnızca aşağıdaki "DOLAP — KAPALI LİSTE" satırlarında yazan gerçek kıyafet ve \
                aksesuarları kullan. Listede yoksa yok demektir; uydurma.
                - Parfüm için yalnızca "NİCHE KOKU RAFI" listesindeki şişeleri an. Raf boşsa koku uydurma; \
                rafı doldurmasını kibarca söyle.
                - Teknik ID, veritabanı numarası veya ham İngilizce kategori kodu kullanma \
                ("t-shirt", "#3"). Estetik Türkçe ad kullan: "navy tişört", "minimalist siyah üst".

                DİL — SAF TÜRKÇE:
                - Kusursuz ve saf Türkçe yaz. Asla Türkçe köke İngilizce ek yapıştırma \
                (YASAK örnekler: "conditionsine", "designin", "absenceindedir", "outfitin", "looku").
                - İngilizce-Türkçe melez cümle kurma. Marka/parfüm adı hariç yabancı sözcük ekleme.
                - Anlamsız veya uydurma kelime üretme. Emin değilsen o kelimeyi yazma; sade kal.
                - Emoji, abartılı satış dili ve teknik jargon yok.

                İMAJ:
                - Hava (sıcaklık/nem/koşul) + listedeki parçalar + (varsa) niche koku = tek bütüncül imaj.
                - Ham meteoroloji raporu okuma; sahneyi atmosfere çevir.
                """;
    }

    static String outputContract() {
        return """
                === YANIT BİÇİMİ (ZORUNLU, KISA) ===
                Yalnızca şu yapıyı kullan; başka bölüm ekleme:

                1) Bir cümlelik sahne (hava + ruh hali).
                2) Doğrudan net, sofistike kombin önerisi: yalnızca kapalı listedeki parçalar \
                (Markdown: **parça adı** ile vurgula). En fazla 3–4 satır veya madde.
                3) Varsa listedeki tek bir parfümü bağla; yoksa koku uydurma.
                4) Tek satır: _Aura notu: …_ (en fazla bir cümle).

                Toplam yanıt kısa kalsın. Uzun deneme, hikâye veya liste dışı doğaçlama YASAK.

                İYİ ÖRNEK (biçim):
                Bugün 26°C — ferah, kontrollü bir siluet.
                **navy tişört** + **siyah pantolon**; üzerine gerekmez.
                Koku: **Acqua di Parma — Colonia**.
                _Aura notu: az parça, net çizgi._

                KÖTÜ ÖRNEK (YAPMA):
                "Perdeyi conditionsine göre designin absenceindedir; köpek kolu ekle."
                """;
    }

    private static String weatherBlock(WeatherSnapshot weather) {
        return """
                === SAHNE: GÜNCEL HAVA ===
                Konum: %s
                Sıcaklık: %s°C
                Nem: %.0f%%
                Koşul: %s
                """.formatted(
                weather.locationName(),
                String.format(Locale.US, "%.1f", weather.temperatureCelsius()),
                weather.humidityPercent(),
                weather.condition());
    }

    private static String wardrobeBlock(List<WardrobeItem> wardrobe) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== DOLAP — KAPALI LİSTE (").append(wardrobe.size()).append(" parça) ===\n");
        sb.append("Yalnızca bu satırlar gerçek envanterdir. Dışarıdan parça EKLEME.\n");
        if (wardrobe.isEmpty()) {
            sb.append("(liste boş — kombin uydurma; dolaba parça eklemesini öner)\n");
            return sb.toString();
        }
        wardrobe.stream().limit(MAX_WARDROBE_LINES).forEach(item ->
                sb.append("- ").append(describePiece(item)).append('\n'));
        if (wardrobe.size() > MAX_WARDROBE_LINES) {
            sb.append("… ve ")
                    .append(wardrobe.size() - MAX_WARDROBE_LINES)
                    .append(" parça daha (yine yalnızca listeden seç)\n");
        }
        return sb.toString();
    }

    private static String perfumeBlock(List<UserPerfume> shelf) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== NİCHE KOKU RAFI — KAPALI LİSTE (").append(shelf.size()).append(" şişe) ===\n");
        if (shelf.isEmpty()) {
            sb.append("(boş — parfüm adı uydurma)\n");
            return sb.toString();
        }
        shelf.stream().limit(MAX_PERFUME_LINES).forEach(perfume ->
                sb.append("- ").append(describePerfume(perfume)).append('\n'));
        return sb.toString();
    }

    /** Kullaniciya yonelik estetik parca adi (ID yok). */
    public static String describePiece(WardrobeItem item) {
        String category = humanCategory(item.getCategory());
        String color = humanColor(item.getColor());
        if (color != null) {
            return color + " " + category;
        }
        return "seçilmiş " + category;
    }

    public static String describePerfume(UserPerfume perfume) {
        StringBuilder sb = new StringBuilder();
        sb.append(perfume.getBrand()).append(" — ").append(perfume.getName());
        if (perfume.getConcentration() != null && !perfume.getConcentration().isBlank()) {
            sb.append(" (").append(perfume.getConcentration()).append(')');
        }
        if (perfume.getChords() != null && !perfume.getChords().isBlank()) {
            sb.append(" · akorlar: ").append(perfume.getChords().replace(',', '·'));
        }
        return sb.toString();
    }

    static String humanCategory(String raw) {
        if (raw == null || raw.isBlank()) {
            return "parça";
        }
        String key = raw.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        return switch (key) {
            case "t-shirt", "tshirt", "tee" -> "tişört";
            case "shirt" -> "gömlek";
            case "blouse" -> "bluz";
            case "sweater", "knit" -> "kazak";
            case "hoodie" -> "hoodie";
            case "jacket" -> "ceket";
            case "coat" -> "palto";
            case "blazer" -> "blazer";
            case "pants", "trousers" -> "pantolon";
            case "jeans" -> "jean";
            case "shorts" -> "şort";
            case "skirt" -> "etek";
            case "dress" -> "elbise";
            case "sneakers" -> "spor ayakkabı";
            case "shoes", "loafers" -> "ayakkabı";
            case "boots" -> "bot";
            case "watch" -> "saat";
            case "bag", "handbag" -> "çanta";
            case "belt" -> "kemer";
            case "scarf" -> "atkı";
            case "hat", "cap" -> "şapka";
            case "accessory" -> "aksesuar";
            default -> raw.trim().toLowerCase(Locale.ROOT);
        };
    }

    static String humanColor(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.trim().toLowerCase(Locale.ROOT);
        return switch (key) {
            case "black", "siyah" -> "siyah";
            case "white", "beyaz" -> "beyaz";
            case "navy", "lacivert" -> "navy";
            case "grey", "gray", "gri" -> "gri";
            case "beige", "bej" -> "bej";
            case "brown", "kahverengi" -> "kahverengi";
            case "blue", "mavi" -> "mavi";
            case "green", "yeşil", "yesil" -> "yeşil";
            case "red", "kırmızı", "kirmizi" -> "kırmızı";
            case "cream", "krem" -> "krem";
            case "champagne" -> "şampanya tonu";
            case "olive" -> "zeytin yeşili";
            default -> key;
        };
    }
}
