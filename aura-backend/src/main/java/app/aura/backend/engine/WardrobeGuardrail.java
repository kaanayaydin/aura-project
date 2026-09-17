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

        Set<String> allowedLabels = buildAllowedLabels(wardrobe, shelf);
        Set<String> allowedTokens = buildAllowedTokens(allowedLabels);

        String[] lines = rawReply.replace("\r\n", "\n").split("\n", -1);
        List<String> kept = new ArrayList<>();
        int dropped = 0;

        for (String line : lines) {
            String scrubbed = scrubLine(line, allowedLabels, allowedTokens);
            if (scrubbed == null) {
                dropped++;
                continue;
            }
            kept.add(scrubbed);
        }

        String joined = collapseBlankLines(kept).strip();
        boolean mutated = dropped > 0 || !joined.equals(rawReply.strip());

        if (joined.isBlank() || !mentionsAllowedInventory(joined, allowedTokens, allowedLabels)) {
            if (!wardrobe.isEmpty() || !shelf.isEmpty()) {
                return new FilterResult(safeOutfit(wardrobe, shelf, weather), true, dropped);
            }
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
                : "%.0f°C, %s — %s".formatted(
                        weather.temperatureCelsius(),
                        weather.condition(),
                        weather.locationName());

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

    private static String scrubLine(
            String line,
            Set<String> allowedLabels,
            Set<String> allowedTokens) {
        if (line == null) {
            return null;
        }
        String trimmed = line.stripTrailing();
        if (trimmed.isBlank()) {
            return trimmed;
        }

        // Once bold uydurma nesneleri cikar; sonra kalan metinde yasagi denetle.
        String withoutBadBold = scrubBoldClaims(trimmed, allowedLabels, allowedTokens);
        if (withoutBadBold.isBlank()) {
            return null;
        }
        if (containsForbidden(withoutBadBold)) {
            return null;
        }
        return withoutBadBold;
    }

    private static String scrubBoldClaims(
            String line,
            Set<String> allowedLabels,
            Set<String> allowedTokens) {
        Matcher matcher = BOLD.matcher(line);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String claim = matcher.group(1).strip();
            if (isAllowedClaim(claim, allowedLabels, allowedTokens)) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement("**" + claim + "**"));
            } else {
                // Dolapta olmayan vurgulu nesneyi tamamen cikar.
                matcher.appendReplacement(sb, "");
            }
        }
        matcher.appendTail(sb);
        return sb.toString()
                .replaceAll(" {2,}", " ")
                .replaceAll(" \\+", "")
                .replaceAll("\\+ ", "")
                .replaceAll("\\s+,", ",")
                .replaceAll(",\\s*,", ",")
                .strip();
    }

    static boolean isAllowedClaim(
            String claim,
            Set<String> allowedLabels,
            Set<String> allowedTokens) {
        String norm = normalize(claim);
        if (norm.isBlank()) {
            return false;
        }
        // Saf atmosfer / meta ifadeler: kisa ve yasak icermiyorsa tut.
        if (norm.matches(".*\\d+°?c.*") || norm.contains("aura notu")) {
            return true;
        }
        for (String label : allowedLabels) {
            if (norm.equals(label) || norm.contains(label) || label.contains(norm)) {
                return true;
            }
        }
        Set<String> claimTokens = tokens(norm);
        if (claimTokens.isEmpty()) {
            return false;
        }
        long hits = claimTokens.stream().filter(allowedTokens::contains).count();
        // En az bir envanter tokeni ve yasak yok.
        return hits >= 1 && claimTokens.stream().noneMatch(FORBIDDEN::contains);
    }

    private static boolean mentionsAllowedInventory(
            String text,
            Set<String> allowedTokens,
            Set<String> allowedLabels) {
        String norm = normalize(text);
        for (String label : allowedLabels) {
            if (!label.isBlank() && norm.contains(label)) {
                return true;
            }
        }
        Set<String> textTokens = tokens(norm);
        return textTokens.stream().anyMatch(allowedTokens::contains);
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

    private static Set<String> buildAllowedLabels(
            List<WardrobeItem> wardrobe,
            List<UserPerfume> shelf) {
        Set<String> labels = new LinkedHashSet<>();
        if (wardrobe != null) {
            for (WardrobeItem item : wardrobe) {
                labels.add(normalize(AuraStylistPrompt.describePiece(item)));
                if (item.getCategory() != null) {
                    labels.add(normalize(AuraStylistPrompt.humanCategory(item.getCategory())));
                }
                if (item.getColor() != null) {
                    String color = AuraStylistPrompt.humanColor(item.getColor());
                    if (color != null) {
                        labels.add(normalize(color));
                    }
                }
            }
        }
        if (shelf != null) {
            for (UserPerfume perfume : shelf) {
                labels.add(normalize(AuraStylistPrompt.describePerfume(perfume)));
                if (perfume.getBrand() != null) {
                    labels.add(normalize(perfume.getBrand()));
                }
                if (perfume.getName() != null) {
                    labels.add(normalize(perfume.getName()));
                }
            }
        }
        labels.removeIf(String::isBlank);
        return labels;
    }

    private static Set<String> buildAllowedTokens(Set<String> allowedLabels) {
        Set<String> tokens = new LinkedHashSet<>();
        for (String label : allowedLabels) {
            tokens.addAll(tokens(label));
        }
        // Genel guvenli stil kelimeleri (nesne degil)
        tokens.addAll(Set.of(
                "kombin", "siluet", "hava", "sahne", "koku", "parfüm", "parfum",
                "aura", "notu", "listeden", "dolap", "raf"));
        tokens.removeIf(token -> token.length() < 2 || FORBIDDEN.contains(token));
        return tokens;
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
