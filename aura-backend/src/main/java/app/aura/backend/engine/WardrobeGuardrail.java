package app.aura.backend.engine;

import app.aura.backend.dto.WeatherSnapshot;
import app.aura.backend.model.UserPerfume;
import app.aura.backend.model.WardrobeItem;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Strict Wardrobe Guardrail — LLM yanitini gercek dolap envanterine baglar.
 *
 * Dolapta olmayan nesneleri (perde, masa, kalem…) satır/ögelerden ayiklar;
 * asiri bozulursa yalnizca envanterden guvenli bir kombin uretir.
 */
public final class WardrobeGuardrail {

    private static final Pattern BOLD = Pattern.compile("\\*\\*([^*]+)\\*\\*");
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}']+");
    private static final Pattern SENTENCE_MARK = Pattern.compile("(?<!\\d)[.!?]");

    /**
     * Tanınan eşya kökleri (fold edilmiş). Kalın parça ancak bunlardan birini
     * içerirse iddiadır; sayı, sıfat ve etiket iddia değildir.
     */
    private static final Set<String> GARMENT_STEMS = Set.of(
            "kravat", "papyon", "atki", "bere", "sapka", "kasket", "eldiven", "kemer",
            "corap", "kolye", "bilezik", "yuzuk", "kupe", "gozluk", "canta", "cuzdan",
            "palto", "mont", "kaban", "yagmurluk", "trenckot", "ceket", "blazer", "hirka",
            "suveter", "sweatshirt", "hoodie", "gomlek", "tisort", "pantolon",
            "jean", "kot", "sort", "etek", "elbise", "tulum", "ayakkabi", "bot", "cizme",
            "sneaker", "loafer", "terlik", "sandalet", "bluz", "yelek", "tayt", "polo",
            "top", "saatim",
            "bowtie", "scarf", "shawl", "beanie", "glove", "gloves",
            "belt", "sock", "socks", "necklace", "bracelet", "earring", "earrings",
            "watch", "glasses", "handbag", "wallet", "coat", "parka", "trench",
            "trenchcoat", "jacket", "cardigan", "sweater", "shirt", "tshirt",
            "pants", "trousers", "jeans", "shorts", "skirt", "dress", "jumpsuit", "romper",
            "shoes", "boots", "slippers", "slipper", "sandal", "sandals", "blouse",
            "sneakers", "legging", "leggings", "vest");

    private static final List<Set<String>> SYNONYM_GROUPS = List.of(
            Set.of("tisort", "tshirt", "tee"),
            Set.of("gomlek", "shirt", "bluz", "blouse"),
            Set.of("pantolon", "pants", "trousers"),
            Set.of("jean", "jeans", "kot"),
            Set.of("sort", "shorts"),
            Set.of("etek", "skirt"),
            Set.of("elbise", "dress"),
            Set.of("ceket", "jacket"),
            Set.of("palto", "coat"),
            Set.of("mont", "parka"),
            Set.of("kazak", "sweater", "suveter", "sweatshirt"),
            Set.of("hirka", "cardigan"),
            Set.of("yelek", "vest"),
            Set.of("tayt", "legging", "leggings"),
            Set.of("ayakkabi", "sneakers", "sneaker", "shoes", "loafer"),
            Set.of("bot", "boots", "cizme"),
            Set.of("tulum", "jumpsuit", "romper"),
            Set.of("gozluk", "glasses"),
            Set.of("watch", "saatim", "kolsaati"),
            Set.of("sapka", "kasket", "bere", "beanie"),
            Set.of("canta", "handbag", "wallet", "cuzdan"),
            Set.of("kemer", "belt"),
            Set.of("atki", "scarf", "shawl"));

    /** Dört harf ve üstü olup ekli hali masum kelimeye çarpan kökler. */
    private static final Set<String> EXACT_STEMS = Set.of("sort", "mont");

    static final String EMPTY_SHELF_LINE =
            "Parfüm rafın boş; istersen bir şişe ekleyebilirsin.";

    /** Kiyafet disi / sik halusine ugrayan nesneler. */
    private static final Set<String> FORBIDDEN = Set.of(
            "perde", "perdeler", "curtain", "curtains",
            "masa", "masalar", "table", "tables",
            "sandalye", "sandalyeler", "chair", "chairs",
            "koltuk", "koltuklar", "sofa", "kanepe",
            "kalem", "kalemler", "pen", "pencil",
            "defter", "kitap", "kitaplar", "book",
            "duvar", "duvarlar", "wall", "walls",
            "pencere", "pencereler", "window", "windows",
            "çiçek", "cicek", "çiçekler", "flower", "flowers",
            "vazo", "halı", "hali", "carpet", "rug",
            "avize", "lamba", "lamp",
            "köpek", "kopek", "kedi", "dog", "cat",
            "araba", "bisiklet", "car", "bike",
            "monitör", "monitor", "laptop", "klavye",
            "buzdolabı", "buzdolabi", "fridge",
            "tablo", "painting", "poster");

    private WardrobeGuardrail() {
    }

    public record FilterResult(String reply, boolean mutated, int droppedLines) {
    }

    /**
     * Ham model yanitini envanterle uyumlu hale getirir.
     */
    public static FilterResult filter(
            String rawReply,
            List<WardrobeItem> wardrobe,
            List<UserPerfume> shelf,
            WeatherSnapshot weather) {
        if (rawReply == null || rawReply.isBlank()) {
            return new FilterResult(safeOutfit(wardrobe, shelf, weather), true, 0);
        }

        Set<String> covered = coveredStems(wardrobe);

        String[] lines = rawReply.replace("\r\n", "\n").split("\n", -1);
        List<String> kept = new ArrayList<>();
        int dropped = 0;
        boolean emptyShelfNoteAdded = false;
        boolean shelfPerfumeRewritten = false;
        boolean shelfEmpty = shelf == null || shelf.isEmpty();

        for (String line : lines) {
            String scrubbed = scrubLine(line, covered);
            if (scrubbed == null) {
                dropped++;
                continue;
            }
            if (isCopiedLabelLine(scrubbed)) {
                dropped++;
                continue;
            }
            if (isKokuLabel(scrubbed)) {
                if (shelfEmpty || isEmptyLabelLine(scrubbed) || !mentionsShelf(scrubbed, shelf)) {
                    dropped++;
                    if (shelfEmpty) {
                        if (!emptyShelfNoteAdded) {
                            kept.add(EMPTY_SHELF_LINE);
                            emptyShelfNoteAdded = true;
                        }
                    } else if (!shelfPerfumeRewritten) {
                        kept.add(shelfPerfumeLine(shelf));
                        shelfPerfumeRewritten = true;
                    }
                    continue;
                }
                kept.add(scrubbed);
                continue;
            }
            if (isEmptyLabelLine(scrubbed) || isInventedItalicPerfume(scrubbed, shelf)) {
                dropped++;
                continue;
            }
            kept.add(scrubbed);
        }

        String joined = collapseBlankLines(kept).strip();
        boolean mutated = dropped > 0 || !joined.equals(rawReply.strip());

        if (joined.isBlank() && (wardrobe != null && !wardrobe.isEmpty() || shelf != null && !shelf.isEmpty())) {
            return new FilterResult(safeOutfit(wardrobe, shelf, weather), true, dropped);
        }

        // Yasak kelime kalintisi varsa guvenli kombine dus.
        if (containsForbidden(joined) && !wardrobe.isEmpty()) {
            return new FilterResult(safeOutfit(wardrobe, shelf, weather), true, dropped + 1);
        }

        return new FilterResult(joined, mutated, dropped);
    }

    /** Yalnizca envanterden uretilmis guvenli markdown yanit. */
    public static String safeOutfit(
            List<WardrobeItem> wardrobe,
            List<UserPerfume> shelf,
            WeatherSnapshot weather) {
        String scene = weather == null
                ? "bugün"
                : WeatherDisplay.summary(weather);

        if (wardrobe == null || wardrobe.isEmpty()) {
            return """
                    Bugün sahne **%s**.

                    Dolap listesi boş — kombin uydurmuyorum.

                    _Aura notu: önce envanter, sonra stil._
                    """.formatted(scene).strip();
        }

        String pieces = wardrobe.stream()
                .limit(4)
                .map(AuraStylistPrompt::describePiece)
                .map(label -> "**" + label + "**")
                .collect(Collectors.joining(" + "));

        String perfume = (shelf == null || shelf.isEmpty())
                ? "Parfüm önermiyorum; raf listesi boş."
                : "Koku: **" + AuraStylistPrompt.describePerfume(shelf.getFirst()) + "**.";

        return """
                Bugün sahne **%s**.

                Kombin (yalnızca dolap listesinden): %s.
                %s

                _Aura notu: listede yoksa yok — uydurma yok._
                """.formatted(scene, pieces, perfume).strip();
    }

    private static String scrubLine(String line, Set<String> covered) {
        if (line == null) {
            return null;
        }
        String trimmed = line.stripTrailing();
        if (trimmed.isBlank()) {
            return trimmed;
        }

        // Koku etiketi satırın tamamıdır; boş-raf cümlesi ayrı eklenir.
        if (isKokuLabel(trimmed)) {
            String withoutBadBold = scrubBoldClaims(trimmed, covered);
            if (withoutBadBold.isBlank() || containsForbidden(withoutBadBold)) {
                return null;
            }
            return withoutBadBold;
        }
        if (containsDisallowedBold(trimmed, covered)) {
            if (isListItem(trimmed)) {
                return null;
            }
            String kept = dropSentencesWithDisallowedBold(trimmed, covered);
            if (kept.isBlank() || containsForbidden(kept)) {
                return null;
            }
            return kept;
        }
        if (containsForbidden(trimmed)) {
            return null;
        }
        return trimmed;
    }

    /** Madde işaretli satırda uydurma kalın iddia varsa satırın tamamı düşer. */
    static boolean isListItem(String line) {
        String s = line.stripLeading();
        if (s.startsWith("- ") || s.startsWith("* ") || s.startsWith("• ")) {
            return true;
        }
        int i = 0;
        while (i < s.length() && i < 3 && Character.isDigit(s.charAt(i))) {
            i++;
        }
        if (i == 0 || i >= s.length()) {
            return false;
        }
        char mark = s.charAt(i);
        return (mark == '.' || mark == ')') && i + 1 < s.length() && s.charAt(i + 1) == ' ';
    }

    /**
     * Uydurma kalın iddia cümlenin içinden kesilmez; cümle bütün olarak atılır.
     * Sınır: . ! ? (rakamdan sonra gelen nokta değil).
     */
    static String dropSentencesWithDisallowedBold(String line, Set<String> covered) {
        Matcher marks = SENTENCE_MARK.matcher(line);
        StringBuilder kept = new StringBuilder();
        int start = 0;
        while (marks.find()) {
            int end = marks.end();
            String sentence = line.substring(start, end);
            if (!containsDisallowedBold(sentence, covered)) {
                kept.append(sentence);
            }
            start = end;
        }
        if (start < line.length()) {
            String sentence = line.substring(start);
            if (!containsDisallowedBold(sentence, covered)) {
                kept.append(sentence);
            }
        }
        return kept.toString().strip();
    }

    private static boolean containsDisallowedBold(String text, Set<String> covered) {
        Matcher matcher = BOLD.matcher(text);
        while (matcher.find()) {
            if (isDisallowedClaim(matcher.group(1), covered)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tanınan eşya veya yasak nesne yoksa iddia değildir. Varsa ve dolapta
     * karşılığı yoksa iddiadır.
     */
    private static boolean isDisallowedClaim(String claim, Set<String> covered) {
        List<String> words = claimTokens(claim);
        if (hasKolSaati(words) && !covers("watch", covered)) {
            return true;
        }
        for (String token : words) {
            if (matchesForbidden(token)) {
                return true;
            }
            String stem = garmentStem(token);
            if (stem != null && !covers(stem, covered)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasKolSaati(List<String> words) {
        for (int i = 0; i + 1 < words.size(); i++) {
            if (words.get(i).equals("kol") && words.get(i + 1).equals("saati")) {
                return true;
            }
        }
        return false;
    }

    /** Üç harf ve altı, ayrıca çarpışan kökler: yalnızca tam kelime. */
    private static String garmentStem(String token) {
        String best = null;
        for (String stem : GARMENT_STEMS) {
            boolean match = stem.length() <= 3 || EXACT_STEMS.contains(stem)
                    ? token.equals(stem)
                    : WardrobeFacts.matchesStem(token, stem);
            if (match && (best == null || stem.length() > best.length())) {
                best = stem;
            }
        }
        return best;
    }

    private static boolean covers(String stem, Set<String> covered) {
        if (covered.contains(stem)) {
            return true;
        }
        for (String present : covered) {
            if (WardrobeFacts.matchesStem(present, stem)
                    || WardrobeFacts.matchesStem(stem, present)
                    || sameSynonym(present, stem)) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameSynonym(String left, String right) {
        for (Set<String> group : SYNONYM_GROUPS) {
            if (group.contains(left) && group.contains(right)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesForbidden(String token) {
        for (String word : FORBIDDEN) {
            if (WardrobeFacts.matchesStem(token, WardrobeFacts.fold(word))) {
                return true;
            }
        }
        return false;
    }

    private static List<String> claimTokens(String claim) {
        List<String> words = new ArrayList<>();
        Matcher matcher = WORD.matcher(WardrobeFacts.fold(claim).replace("-", ""));
        while (matcher.find()) {
            words.add(matcher.group());
        }
        return words;
    }

    private static Set<String> coveredStems(List<WardrobeItem> wardrobe) {
        Set<String> covered = new LinkedHashSet<>();
        if (wardrobe == null) {
            return covered;
        }
        for (WardrobeItem item : wardrobe) {
            if (item.getCategory() == null) {
                continue;
            }
            addCovered(covered, AuraStylistPrompt.humanCategory(item.getCategory()));
            addCovered(covered, item.getCategory());
        }
        Set<String> extra = new LinkedHashSet<>();
        for (String token : covered) {
            for (Set<String> group : SYNONYM_GROUPS) {
                if (group.contains(token)) {
                    extra.addAll(group);
                }
            }
        }
        covered.addAll(extra);
        return covered;
    }

    private static void addCovered(Set<String> covered, String text) {
        Matcher matcher = WORD.matcher(WardrobeFacts.fold(text).replace("-", ""));
        while (matcher.find()) {
            if (matcher.group().length() >= 2) {
                covered.add(matcher.group());
            }
        }
    }

    private static String scrubBoldClaims(String line, Set<String> covered) {
        Matcher matcher = BOLD.matcher(line);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            if (isDisallowedClaim(matcher.group(1), covered)) {
                matcher.appendReplacement(sb, "");
            } else {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(sb);
        return tidySeparators(sb.toString());
    }

    /**
     * Ortadan silinen kalın iddia "a + + b" bırakır; tek "+" durur.
     * Geçerli iki parça arasındaki ayraç silinmez.
     */
    static String tidySeparators(String line) {
        String cleaned = line.replaceAll("(?:\\s*\\+\\s*){2,}", " + ");
        cleaned = cleaned.replaceAll("^\\s*\\+\\s*", "");
        cleaned = cleaned.replaceAll("\\s*\\+\\s*$", "");
        cleaned = cleaned.replaceAll("\\s*\\+\\s*([.,;])", "$1");
        cleaned = cleaned.replaceAll(" {2,}", " ");
        cleaned = cleaned.replaceAll("\\s+,", ",");
        cleaned = cleaned.replaceAll(",\\s*,", ",");
        return cleaned.strip();
    }

    /** "Koku:" veya "Koku —" etiketi. "Koku bu havada..." gibi düz cümle değil. */
    static boolean isKokuLabel(String line) {
        String folded = normalize(stripMarkdownEdge(line));
        return folded.startsWith("koku:")
                || folded.startsWith("koku —")
                || folded.startsWith("koku -")
                || folded.startsWith("koku–");
    }

    /** Kalın iddia silindikten sonra "Koku: ." gibi boş etiket. */
    static boolean isEmptyLabelLine(String line) {
        String folded = normalize(line.replaceAll("[*_]", " "));
        folded = folded.replaceAll("[\\p{P}\\p{S}]+", " ").replaceAll("\\s+", " ").trim();
        return folded.equals("koku") || folded.equals("kombin");
    }

    static boolean isInventedItalicPerfume(String line, List<UserPerfume> shelf) {
        String trimmed = line.strip();
        if (trimmed.length() < 3 || !trimmed.startsWith("_") || !trimmed.endsWith("_")) {
            return false;
        }
        String inner = trimmed.substring(1, trimmed.length() - 1).strip();
        if (inner.isEmpty() || inner.contains("_")) {
            return false;
        }
        String folded = normalize(inner);
        if (folded.contains("aura notu")) {
            return false;
        }
        return !mentionsShelf(inner, shelf);
    }

    static boolean isCopiedLabelLine(String line) {
        String folded = normalize(stripMarkdownEdge(line));
        if (folded.startsWith("dolabin:")) {
            return true;
        }
        if (folded.startsWith("dolap") && folded.contains("kapali liste")) {
            return true;
        }
        if (folded.startsWith("parfum rafin:")) {
            return true;
        }
        if (folded.startsWith("bugunun havasi:")) {
            return true;
        }
        if (folded.startsWith("kapali liste") || folded.startsWith("niche koku rafi")) {
            return true;
        }
        return folded.startsWith("yalnizca turkce");
    }

    private static String stripMarkdownEdge(String line) {
        return line.strip().replaceAll("^[*_\\s]+", "").replaceAll("[*_\\s]+$", "");
    }

    private static boolean mentionsShelf(String line, List<UserPerfume> shelf) {
        if (shelf == null || shelf.isEmpty() || line == null) {
            return false;
        }
        String folded = normalize(line);
        for (UserPerfume perfume : shelf) {
            if (perfume.getBrand() != null && !perfume.getBrand().isBlank()
                    && folded.contains(normalize(perfume.getBrand()))) {
                return true;
            }
            if (perfume.getName() != null && !perfume.getName().isBlank()
                    && folded.contains(normalize(perfume.getName()))) {
                return true;
            }
        }
        return false;
    }

    private static String shelfPerfumeLine(List<UserPerfume> shelf) {
        return "Koku: **" + AuraStylistPrompt.describePerfume(shelf.getFirst()) + "**.";
    }

    static boolean containsForbidden(String text) {
        Set<String> found = tokens(normalize(text));
        for (String token : found) {
            if (FORBIDDEN.contains(token)) {
                return true;
            }
        }
        // "kopek kolu" gibi bitisik/ayrik ifadeler
        String compact = normalize(text).replace(" ", "");
        return compact.contains("kopekkolu") || compact.contains("köpekkolu");
    }

    private static Set<String> tokens(String text) {
        Set<String> out = new LinkedHashSet<>();
        Matcher matcher = WORD.matcher(text);
        while (matcher.find()) {
            out.add(matcher.group().toLowerCase(Locale.ROOT));
        }
        return out;
    }

    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT)
                .replace("\u0307", "")
                .replace('İ', 'i')
                .replace('I', 'ı')
                .replace('ı', 'i') // eslestirme icin sade
                .replace('ş', 's')
                .replace('Ş', 's')
                .replace('ğ', 'g')
                .replace('ü', 'u')
                .replace('ö', 'o')
                .replace('ç', 'c')
                .trim();
    }

    private static String collapseBlankLines(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        boolean prevBlank = false;
        for (String line : lines) {
            boolean blank = line.isBlank();
            if (blank && prevBlank) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(line);
            prevBlank = blank;
        }
        return sb.toString();
    }
}
