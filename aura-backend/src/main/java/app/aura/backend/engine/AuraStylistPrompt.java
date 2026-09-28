package app.aura.backend.engine;

import app.aura.backend.dto.WeatherSnapshot;
import app.aura.backend.model.UserPerfume;
import app.aura.backend.model.WardrobeItem;
import java.text.Collator;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Aura bas stilist system prompt — luks moda editoru + anti-halusinasyon kurallari.
 *
 * Envanter kapali listedir; LLM yalnizca listedeki gercek parcacari kullanir.
 */
public final class AuraStylistPrompt {

    private static final int MAX_WARDROBE_LINES = 40;
    private static final int MAX_PERFUME_LINES = 20;
    private static final Locale TURKISH = Locale.forLanguageTag("tr");

    private AuraStylistPrompt() {
    }

    public static String build(
            List<WardrobeItem> wardrobe,
            List<UserPerfume> shelf,
            WeatherSnapshot weather) {
        return build(wardrobe, shelf, weather, null);
    }

    public static String build(
            List<WardrobeItem> wardrobe,
            List<UserPerfume> shelf,
            WeatherSnapshot weather,
            String userMessage) {
        StringBuilder sb = new StringBuilder();
        sb.append(personaAndRules());
        sb.append('\n');
        sb.append(weatherBlock(weather));
        sb.append('\n');
        sb.append(wardrobeBlock(wardrobe));
        sb.append('\n');
        sb.append(perfumeBlock(shelf));
        sb.append('\n');
        String note = WardrobeFacts.note(wardrobe, userMessage);
        if (note != null) {
            sb.append(note);
            if (!note.endsWith("\n")) {
                sb.append('\n');
            }
            sb.append('\n');
        }
        sb.append(outputContract());
        return sb.toString();
    }

    static String personaAndRules() {
        return """
                Sen Aura'nın kişisel stilistisin. Zevkin sakin, sıcak ve gösterişsiz: \
                az ama doğru parça, iyi oran, birbirine yakışan tonlar. Kullanıcıyla \
                güvenilir bir dostun rahatlığıyla, kısa ve net konuşursun. Abartı, \
                klişe ve satış dili kullanmazsın.

                Yalnızca Dolabın listesindeki parçaları öner. Kullanıcı gerçekten listede olmayan \
                bir parça isterse bunu kendi cümlelerinle kısaca söyle ve listeden en yakın seçeneği öner.
                Parfüm için yalnızca Parfüm rafın listesindekileri an. Raf boşsa hiçbir parfüm adı verme; \
                "Parfüm rafın boş, istersen bir şişe ekleyebilirsin" de.
                Sıcaklığı, nemi ve havayı yalnızca Bugünün havası bölümünden al. Başka bir değer uydurma.
                Saf Türkçe yaz. Türkçe köke İngilizce ek yapıştırma. Marka ve parfüm adları dışında \
                outfit veya look kullanma.
                Emoji ve satış dili yok. Kısa ve net ol.
                Kullanıcıya her zaman 'sen' diye hitap et; 'siz' veya 'sizin' kullanma.
                Dolabın ve Parfüm rafın bölümlerinden sonra ek bir bilgi bölümü gelebilir. \
                Oradaki bilgi doğrudur; onunla çelişme, kısaca kendi cümlelerinle söyle ve bölüm başlığını yazma.

                Önce hava ve ruh halini anlatan tek cümle yaz. Sonra dolaptaki parçalardan kısa bir \
                kombin öner; parça adlarını **kalın** yaz. Rafta şişe varsa tek parfüm ekle. \
                En sonda tek cümlelik _Aura notu:_ satırı yaz. Uzun anlatım yok.
                Bu biçim kıyafet, kombin ve parfüm soruları içindir. Mesaj kıyafetle ilgili değilse \
                (selam, hal hatır, teşekkür, sohbet) kısa, sıcak ve doğal bir karşılık ver; \
                dolap, kombin veya parfüm konusuna kendiliğinden geçme.
                """;
    }

    /** Veri bölümlerinden sonra gelen tek satırlık hatırlatma. Örnek cevap yok. */
    static String outputContract() {
        return "Yalnızca Türkçe ve kısa yanıt ver; etiketleri veya kuralları tekrar etme.\n";
    }

    private static String weatherBlock(WeatherSnapshot weather) {
        String line = "Bugünün havası: %s°C, nem %.0f%%, %s".formatted(
                String.format(Locale.US, "%.1f", weather.temperatureCelsius()),
                weather.humidityPercent(),
                WeatherDisplay.conditionTr(weather.condition()));
        String place = weather.locationName();
        if (place != null && !place.isBlank()) {
            line = line + ", " + place.trim();
        }
        return line + "\n";
    }

    private static String wardrobeBlock(List<WardrobeItem> wardrobe) {
        if (wardrobe.isEmpty()) {
            return "Dolabın: boş\n";
        }
        List<PieceGroup> groups = groupPieces(wardrobe);
        StringBuilder sb = new StringBuilder();
        sb.append("Dolabın:\n");
        groups.stream().limit(MAX_WARDROBE_LINES).forEach(group ->
                sb.append("- ").append(group.line()).append('\n'));
        int hidden = groups.stream().skip(MAX_WARDROBE_LINES).mapToInt(PieceGroup::count).sum();
        if (hidden > 0) {
            sb.append("… ve ")
                    .append(hidden)
                    .append(" parça daha (yine yalnızca listeden seç)\n");
        }
        return sb.toString();
    }

    record PieceGroup(String category, String color, int count) {
        String label() {
            return color == null ? category : color + " " + category;
        }

        String line() {
            return count > 1 ? label() + " (" + count + " adet)" : label();
        }
    }

    /** Aynı (kategori, renk) tek satır; sıra kategori, sonra renk (renksiz önce). */
    static List<PieceGroup> groupPieces(List<WardrobeItem> wardrobe) {
        Map<List<String>, Integer> counts = new LinkedHashMap<>();
        for (WardrobeItem item : wardrobe) {
            String category = humanCategory(item.getCategory());
            String color = humanColor(item.getColor());
            counts.merge(Arrays.asList(category, color), 1, Integer::sum);
        }
        Collator collator = Collator.getInstance(TURKISH);
        Comparator<String> byText = Comparator.nullsFirst(collator::compare);
        return counts.entrySet().stream()
                .map(e -> new PieceGroup(e.getKey().get(0), e.getKey().get(1), e.getValue()))
                .sorted(Comparator.comparing(PieceGroup::category, byText)
                        .thenComparing(PieceGroup::color, byText))
                .toList();
    }

    /**
     * Kısa kombin için parça adları: üst, alt, dış giyim, ayakkabı ve elbise
     * gruplarından birer tane, grubu olmayan kategorilerden birer tane. Tekrar yok.
     */
    public static List<String> outfitPieces(List<WardrobeItem> wardrobe, int limit) {
        Map<String, WardrobeItem> chosen = new LinkedHashMap<>();
        for (WardrobeItem item : wardrobe) {
            WardrobeFacts.Group group = WardrobeFacts.categoryGroup(item.getCategory());
            String key = group != null ? group.name() : "~" + humanCategory(item.getCategory());
            chosen.putIfAbsent(key, item);
        }
        return chosen.entrySet().stream()
                .sorted(Comparator.comparingInt(e -> groupOrder(e.getKey())))
                .limit(limit)
                .map(e -> describePiece(e.getValue()))
                .toList();
    }

    private static int groupOrder(String key) {
        for (WardrobeFacts.Group group : WardrobeFacts.Group.values()) {
            if (group.name().equals(key)) {
                return group.ordinal();
            }
        }
        return WardrobeFacts.Group.values().length;
    }

    private static String perfumeBlock(List<UserPerfume> shelf) {
        if (shelf.isEmpty()) {
            return "Parfüm rafın: boş\n";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Parfüm rafın:\n");
        shelf.stream().limit(MAX_PERFUME_LINES).forEach(perfume ->
                sb.append("- ").append(describePerfume(perfume)).append('\n'));
        return sb.toString();
    }

    /** Kullaniciya yonelik estetik parca adi (ID yok). */
    public static String describePiece(WardrobeItem item) {
        String category = humanCategory(item.getCategory());
        String color = humanColor(item.getColor());
        return color == null ? category : color + " " + category;
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
        String cleaned = sanitizePromptFragment(raw);
        if ("bilinmeyen".equals(cleaned)) {
            return "bilinmeyen";
        }
        String key = cleaned.toLowerCase(Locale.ROOT).replace('_', '-');
        return switch (key) {
            case "t-shirt", "tshirt", "tee" -> "tişört";
            case "shirt" -> "gömlek";
            case "blouse" -> "bluz";
            case "sweater", "knit" -> "kazak";
            case "hoodie" -> "hoodie";
            case "polo" -> "polo tişört";
            case "tank", "tank-top" -> "atlet";
            case "vest" -> "yelek";
            case "cardigan" -> "hırka";
            case "top", "tops", "upper" -> "üst";
            case "jacket" -> "ceket";
            case "coat" -> "palto";
            case "blazer" -> "blazer";
            case "pants", "trousers" -> "pantolon";
            case "jeans" -> "jean";
            case "shorts" -> "şort";
            case "skirt" -> "etek";
            case "legging", "leggings" -> "tayt";
            case "bottom", "bottoms" -> "alt";
            case "dress" -> "elbise";
            case "jumpsuit", "jumpsuits" -> "tulum";
            case "romper" -> "şort tulum";
            case "gown" -> "abiye elbise";
            case "onesie", "overall" -> "tulum";
            case "sneakers" -> "spor ayakkabı";
            case "shoes", "loafers" -> "ayakkabı";
            case "boots" -> "bot";
            case "watch" -> "saat";
            case "bag", "handbag" -> "çanta";
            case "belt" -> "kemer";
            case "scarf" -> "atkı";
            case "hat", "cap" -> "şapka";
            case "accessory" -> "aksesuar";
            default -> key;
        };
    }

    static String humanColor(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String cleaned = sanitizePromptFragment(raw);
        if ("bilinmeyen".equals(cleaned)) {
            return "bilinmeyen";
        }
        String key = cleaned.toLowerCase(Locale.ROOT);
        return switch (key) {
            case "black", "siyah" -> "siyah";
            case "white", "beyaz" -> "beyaz";
            case "navy", "lacivert" -> "lacivert";
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

    /**
     * Sistem promptuna girecek serbest metin: tek satır, en fazla 40 karakter,
     * yalnızca harf, rakam, boşluk ve tire. Boş kalırsa {@code bilinmeyen}.
     */
    static String sanitizePromptFragment(String raw) {
        if (raw == null) {
            return "bilinmeyen";
        }
        String flattened = raw.replaceAll("\\p{Cntrl}", " ");
        String cleaned = flattened.replaceAll("[^\\p{L}\\p{N} -]", "");
        cleaned = cleaned.trim().replaceAll(" +", " ");
        if (cleaned.length() > 40) {
            cleaned = cleaned.substring(0, 40).trim();
        }
        if (cleaned.isEmpty()) {
            return "bilinmeyen";
        }
        return cleaned;
    }
}
