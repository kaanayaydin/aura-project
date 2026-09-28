package app.aura.backend.engine;

import app.aura.backend.model.WardrobeItem;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dolap kategorilerinden hesaplanan olgu. Prompta yalnızca eksik sorusu
 * veya dolapta olmayan bir parça isteği varsa girer. Kullanıcı metnini kopyalamaz.
 */
public final class WardrobeFacts {

    enum Group {
        UST("üst"),
        ALT("alt"),
        OUTER("dış giyim"),
        SHOES("ayakkabı"),
        ONE_PIECE("elbise");

        final String label;

        Group(String label) {
            this.label = label;
        }
    }

    private record Ask(String canonical, Group group) {
    }

    private static final Map<String, Group> CATEGORY_GROUPS = new LinkedHashMap<>();
    private static final List<Ask> ASKS = List.of(
            new Ask("ceket", Group.OUTER),
            new Ask("mont", Group.OUTER),
            new Ask("kaban", Group.OUTER),
            new Ask("palto", Group.OUTER),
            new Ask("blazer", Group.OUTER),
            new Ask("ayakkabı", Group.SHOES),
            new Ask("sneaker", Group.SHOES),
            new Ask("bot", Group.SHOES),
            new Ask("gömlek", Group.UST),
            new Ask("tişört", Group.UST),
            new Ask("bluz", Group.UST),
            new Ask("kazak", Group.UST),
            new Ask("hırka", Group.UST),
            new Ask("pantolon", Group.ALT),
            new Ask("jean", Group.ALT),
            new Ask("şort", Group.ALT),
            new Ask("etek", Group.ALT),
            new Ask("elbise", Group.ONE_PIECE));

    /**
     * İyelik ve hal ekleri, fold() sonrası. Sıfat eki (-li/-lı) ve meslek
     * eki (-ci/-cı) yok: "elbiseli", "ayakkabıcı" parça isteği sayılmaz.
     * "-siz" yok: "eksiksiz" eksik sayılmaz.
     */
    private static final Set<String> SUFFIXES = Set.of(
            "im", "um", "in", "un", "i", "u", "e", "a",
            "de", "da", "te", "ta", "den", "dan", "ten", "tan", "le", "la",
            "imi", "umu", "ime", "uma", "ima", "ine", "una", "ini", "unu",
            "imle", "umla", "inde", "inda", "unda", "unde",
            "ler", "lar", "lerim", "larim", "leri", "lari", "lerin", "larin",
            "lerimi", "larimi", "lerimle", "larimla",
            "lerime", "larima", "lerine", "larina", "lerini", "larini",
            "imiz", "umuz", "si", "su",
            "m", "n", "mi", "mu", "miz", "muz",
            "mde", "mda", "mle", "mla",
            "nde", "nda", "nden", "ndan", "nin", "nun",
            "imde", "imden");

    /** Üç harfli kökte "rafine", "altına", "üstüne" gibi çarpışmalar. */
    private static final Set<String> SHORT_BLOCKED_RESTS = Set.of(
            "ine", "ini", "ina", "una", "unu", "une");

    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}']+");

    static {
        put(Group.UST, "t-shirt", "tshirt", "tee", "shirt", "blouse", "sweater", "knit",
                "hoodie", "polo", "tank", "tank-top", "vest", "cardigan", "top", "tops", "upper");
        put(Group.ALT, "pants", "trousers", "jeans", "shorts", "skirt", "legging", "leggings",
                "bottom", "bottoms");
        put(Group.OUTER, "jacket", "coat", "blazer");
        put(Group.SHOES, "sneakers", "shoes", "loafers", "boots");
        put(Group.ONE_PIECE, "dress", "jumpsuit", "jumpsuits", "romper", "gown", "onesie", "overall");
    }

    private WardrobeFacts() {
    }

    /**
     * @return {@code Dolap notu:} bloğu, ya da ilgili değilse {@code null}
     */
    public static String note(List<WardrobeItem> wardrobe, String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return null;
        }
        EnumSet<Group> present = presentGroups(wardrobe);
        String folded = fold(userMessage);
        List<String> lines = new ArrayList<>();
        if (asksGap(folded)) {
            List<String> missing = new ArrayList<>();
            for (Group group : Group.values()) {
                if (!present.contains(group)) {
                    missing.add(group.label);
                }
            }
            if (!missing.isEmpty()) {
                lines.add("Dolapta olmayan gruplar: " + String.join(", ", missing) + ".");
            }
        }
        for (Ask ask : ASKS) {
            if (present.contains(ask.group())) {
                continue;
            }
            if (mentions(folded, fold(ask.canonical()))) {
                lines.add("Kullanıcı " + ask.canonical() + " istedi; dolabında "
                        + ask.canonical() + " yok.");
            }
        }
        if (lines.isEmpty()) {
            return null;
        }
        return "Dolap notu:\n" + String.join("\n", lines) + "\n";
    }

    static EnumSet<Group> presentGroups(List<WardrobeItem> wardrobe) {
        EnumSet<Group> present = EnumSet.noneOf(Group.class);
        if (wardrobe == null) {
            return present;
        }
        for (WardrobeItem item : wardrobe) {
            Group group = categoryGroup(item.getCategory());
            if (group != null) {
                present.add(group);
            }
        }
        return present;
    }

    static Group categoryGroup(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        String key = category.trim().toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-');
        return CATEGORY_GROUPS.get(key);
    }

    private static void put(Group group, String... codes) {
        for (String code : codes) {
            CATEGORY_GROUPS.put(code, group);
        }
    }

    static Set<String> categoryCodes() {
        return java.util.Collections.unmodifiableSet(CATEGORY_GROUPS.keySet());
    }

    /**
     * Kelime, kökün kendisi ya da kök + bilinen ek. Üç harften kısa köklerde
     * çarpışan ekler kabul edilmez. Sondaki k, ünlüyle başlayan ekte g olur
     * (kazak → kazağım, eksik → eksiğim).
     */
    static boolean matchesStem(String token, String stem) {
        if (token == null || stem == null || stem.isEmpty() || token.length() < stem.length()) {
            return false;
        }
        if (token.equals(stem)) {
            return true;
        }
        if (token.startsWith(stem)) {
            String rest = token.substring(stem.length());
            if (suffixAllowed(stem, rest)) {
                return true;
            }
        }
        if (stem.length() >= 3 && stem.charAt(stem.length() - 1) == 'k') {
            String soft = stem.substring(0, stem.length() - 1) + "g";
            if (token.startsWith(soft) && token.length() > soft.length()) {
                String rest = token.substring(soft.length());
                char head = rest.charAt(0);
                if (head == 'a' || head == 'e' || head == 'i' || head == 'u' || head == 'o') {
                    return suffixAllowed(stem, rest);
                }
            }
        }
        return false;
    }

    private static boolean suffixAllowed(String stem, String rest) {
        if (rest.isEmpty() || !SUFFIXES.contains(rest)) {
            return false;
        }
        return stem.length() > 3 || !SHORT_BLOCKED_RESTS.contains(rest);
    }

    static boolean mentions(String foldedMessage, String foldedStem) {
        Matcher matcher = WORD.matcher(foldedMessage);
        while (matcher.find()) {
            if (matchesStem(matcher.group(), foldedStem)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Eksik / alışveriş niyeti, kelime sınırında. "eksiksiz", "tamamlanmış"
     * ve "tamamen" tetiklemez.
     */
    static boolean asksGap(String folded) {
        List<String> words = new ArrayList<>();
        Matcher matcher = WORD.matcher(folded);
        while (matcher.find()) {
            words.add(matcher.group());
        }
        for (int i = 0; i < words.size(); i++) {
            String token = words.get(i);
            if (matchesStem(token, "eksik")) {
                return true;
            }
            if (gapVerb(token, "tamamla") || matchesStem(token, "alisveris")) {
                return true;
            }
            if (token.equals("ne") && i + 1 < words.size()) {
                String next = words.get(i + 1);
                if (gapVerb(next, "almali") || gapVerb(next, "alayim")) {
                    return true;
                }
            }
        }
        return false;
    }

    /** "tamamla" + istek eki evet; "tamamlan…" (edilgen) hayır. */
    private static boolean gapVerb(String token, String stem) {
        if (matchesStem(token, stem)) {
            return true;
        }
        if (!token.startsWith(stem) || token.length() <= stem.length()) {
            return false;
        }
        String rest = token.substring(stem.length());
        if (rest.startsWith("n")) {
            return false;
        }
        return rest.startsWith("y") && rest.length() > 1 && SUFFIXES.contains(rest.substring(1));
    }

    static String fold(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT)
                .replace("\u0307", "")
                .replace('ı', 'i')
                .replace('İ', 'i')
                .replace('I', 'i')
                .replace('ş', 's')
                .replace('Ş', 's')
                .replace('ğ', 'g')
                .replace('Ğ', 'g')
                .replace('ü', 'u')
                .replace('Ü', 'u')
                .replace('ö', 'o')
                .replace('Ö', 'o')
                .replace('ç', 'c')
                .replace('Ç', 'c');
    }
}
