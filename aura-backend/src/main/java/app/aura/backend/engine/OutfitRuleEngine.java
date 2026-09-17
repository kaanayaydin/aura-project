package app.aura.backend.engine;

import app.aura.backend.model.WardrobeItem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Kural tabanli kombin motoru + renk uyumu.
 *
 * Parca skoru = guven×0.30 + sicaklik×0.45 + etkinlik×0.25.
 * Alt/aksesuar seciminde renk uyumu ek agirlik alir; final matchScore
 * parca ortalamasi ile renk uyumunun harmanidir.
 */
public final class OutfitRuleEngine {

    public record ScoredPick(
            WardrobeItem item,
            GarmentSlot slot,
            double score,
            String reason) {
    }

    public record OutfitPlan(
            SeasonBand seasonBand,
            Occasion occasion,
            Optional<ScoredPick> top,
            Optional<ScoredPick> bottom,
            Optional<ScoredPick> accessory,
            double matchScore,
            ColorHarmony.Result colorHarmony,
            String vibe,
            String summary,
            List<String> notes) {
    }

    public OutfitPlan suggest(
            List<WardrobeItem> wardrobe,
            double temperatureCelsius,
            Double humidityPercent,
            Occasion occasion) {
        SeasonBand season = SeasonBand.fromCelsius(temperatureCelsius);
        Map<GarmentSlot, List<ScoredPick>> bySlot = scoreAll(
                wardrobe, season, humidityPercent, occasion);

        Set<Long> usedIds = new HashSet<>();
        Optional<ScoredPick> top = pickBest(bySlot.get(GarmentSlot.TOP), usedIds, Optional.empty());

        List<String> notes = new ArrayList<>();
        Optional<ScoredPick> bottom = pickBest(
                bySlot.get(GarmentSlot.BOTTOM),
                usedIds,
                top.map(p -> p.item().getColor()));

        if (top.isPresent() && isDress(top.get().item()) && bottom.isEmpty()) {
            notes.add("Secilen elbise alt giyimi kapsiyor; ayri alt parcaya gerek yok.");
        }

        Optional<ScoredPick> accessory = pickBest(
                bySlot.get(GarmentSlot.ACCESSORY),
                usedIds,
                top.map(p -> p.item().getColor()).or(() -> bottom.map(p -> p.item().getColor())));

        if (top.isEmpty()) {
            notes.add("Dolapta uygun ust giyim bulunamadi.");
        }
        if (bottom.isEmpty() && !(top.isPresent() && isDress(top.get().item()))) {
            notes.add("Dolapta uygun alt giyim bulunamadi.");
        }
        if (accessory.isEmpty()) {
            notes.add("Dolapta uygun aksesuar bulunamadi.");
        }
        if (season == SeasonBand.COLD || season == SeasonBand.COOL) {
            boolean hasJacket = top.map(p -> "jacket".equalsIgnoreCase(p.item().getCategory()))
                    .orElse(false);
            if (!hasJacket && wardrobe.stream().anyMatch(i -> "jacket".equalsIgnoreCase(i.getCategory()))) {
                notes.add("Serin/soguk hava: dolaptaki ceketi ustune almayi dusunun.");
            } else if (!hasJacket) {
                notes.add("Serin/soguk hava icin dolapta ceket yok.");
            }
        }

        ColorHarmony.Result colorHarmony = ColorHarmony.scoreOutfit(
                top.map(p -> p.item().getColor()),
                bottom.map(p -> p.item().getColor()),
                accessory.map(p -> p.item().getColor()));

        if (colorHarmony.type() != ColorHarmony.Type.UNKNOWN) {
            notes.add("Renk uyumu: " + colorHarmony.type().label()
                    + " (" + Math.round(colorHarmony.score() * 100) + "%). "
                    + colorHarmony.explanation());
        }

        double pieceScore = averageScore(top, bottom, accessory);
        double matchScore = round2(pieceScore * 0.75 + colorHarmony.score() * 0.25);
        String vibe = composeVibe(season, occasion, humidityPercent);
        String summary = composeSummary(
                season, occasion, temperatureCelsius, top, bottom, accessory, colorHarmony);

        return new OutfitPlan(
                season, occasion, top, bottom, accessory, matchScore,
                colorHarmony, vibe, summary, List.copyOf(notes));
    }

    private Map<GarmentSlot, List<ScoredPick>> scoreAll(
            List<WardrobeItem> wardrobe,
            SeasonBand season,
            Double humidityPercent,
            Occasion occasion) {
        Map<GarmentSlot, List<ScoredPick>> bySlot = new EnumMap<>(GarmentSlot.class);
        for (GarmentSlot slot : GarmentSlot.values()) {
            if (slot != GarmentSlot.IGNORED) {
                bySlot.put(slot, new ArrayList<>());
            }
        }

        for (WardrobeItem item : wardrobe) {
            GarmentSlot slot = GarmentSlot.ofCategory(item.getCategory());
            if (slot == GarmentSlot.IGNORED) {
                continue;
            }
            double confidence = item.getCategoryConfidence() != null
                    ? clamp(item.getCategoryConfidence())
                    : 0.55;
            double tempScore = temperatureFit(item.getCategory(), season, humidityPercent);
            double occasionScore = occasionFit(item.getCategory(), occasion, slot);
            double score = confidence * 0.30 + tempScore * 0.45 + occasionScore * 0.25;
            String reason = reasonFor(item.getCategory(), season, occasion, tempScore, occasionScore);
            bySlot.get(slot).add(new ScoredPick(item, slot, score, reason));
        }
        return bySlot;
    }

    /**
     * En iyi parcayi secer; `anchorColor` varsa renk uyumunu skora katar.
     */
    private Optional<ScoredPick> pickBest(
            List<ScoredPick> candidates,
            Set<Long> usedIds,
            Optional<String> anchorColor) {
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        return candidates.stream()
                .filter(pick -> pick.item().getId() == null || !usedIds.contains(pick.item().getId()))
                .max(Comparator.comparingDouble(pick -> {
                    double base = pick.score();
                    if (anchorColor.isEmpty()) {
                        return base;
                    }
                    double harmony = ColorHarmony.scorePair(
                            anchorColor.get(), pick.item().getColor()).score();
                    return base * 0.70 + harmony * 0.30;
                }))
                .map(pick -> {
                    if (pick.item().getId() != null) {
                        usedIds.add(pick.item().getId());
                    }
                    return pick;
                });
    }

    double temperatureFit(String category, SeasonBand season, Double humidityPercent) {
        String cat = category.toLowerCase(Locale.ROOT);
        boolean humid = humidityPercent != null && humidityPercent >= 70.0;

        return switch (cat) {
            case "t-shirt" -> switch (season) {
                case HOT -> humid ? 1.0 : 0.95;
                case MILD -> 0.80;
                case COOL -> 0.40;
                case COLD -> 0.15;
            };
            case "shirt" -> switch (season) {
                case HOT -> humid ? 0.45 : 0.55;
                case MILD -> 0.90;
                case COOL -> 0.85;
                case COLD -> 0.55;
            };
            case "jacket" -> switch (season) {
                case HOT -> 0.10;
                case MILD -> 0.45;
                case COOL -> 0.95;
                case COLD -> 1.0;
            };
            case "dress" -> switch (season) {
                case HOT -> 0.90;
                case MILD -> 0.80;
                case COOL -> 0.45;
                case COLD -> 0.20;
            };
            case "pants" -> switch (season) {
                case HOT -> humid ? 0.70 : 0.75;
                case MILD, COOL, COLD -> 0.90;
            };
            case "sneakers" -> switch (season) {
                case HOT, MILD -> 0.90;
                case COOL -> 0.80;
                case COLD -> 0.60;
            };
            case "watch", "glasses" -> 0.75;
            default -> 0.40;
        };
    }

    double occasionFit(String category, Occasion occasion, GarmentSlot slot) {
        String cat = category.toLowerCase(Locale.ROOT);
        return switch (occasion) {
            case MEETING -> switch (cat) {
                case "shirt" -> 1.0;
                case "jacket" -> 0.95;
                case "pants" -> 0.95;
                case "watch", "glasses" -> 0.90;
                case "dress" -> 0.85;
                case "t-shirt" -> 0.35;
                case "sneakers" -> 0.30;
                default -> 0.40;
            };
            case CASUAL -> switch (cat) {
                case "t-shirt" -> 0.95;
                case "sneakers" -> 0.95;
                case "pants" -> 0.90;
                case "shirt" -> 0.75;
                case "jacket" -> 0.70;
                case "dress" -> 0.80;
                case "watch", "glasses" -> 0.70;
                default -> 0.45;
            };
            case SPORT -> switch (cat) {
                case "t-shirt" -> 1.0;
                case "sneakers" -> 1.0;
                case "pants" -> 0.85;
                case "watch" -> 0.55;
                case "glasses" -> 0.40;
                case "shirt", "jacket", "dress" -> 0.15;
                default -> 0.30;
            };
        };
    }

    private String reasonFor(
            String category,
            SeasonBand season,
            Occasion occasion,
            double tempScore,
            double occasionScore) {
        String tempHint = tempScore >= 0.75
                ? season.label() + " havaya uygun"
                : tempScore >= 0.45
                        ? season.label() + " havada kabul edilebilir"
                        : season.label() + " hava icin zayif uyum";
        String occasionHint = occasionScore >= 0.75
                ? occasion.label() + " icin guclu tercih"
                : occasionScore >= 0.45
                        ? occasion.label() + " icin orta tercih"
                        : occasion.label() + " icin dusuk tercih";
        return category + ": " + tempHint + "; " + occasionHint;
    }

    private String composeVibe(SeasonBand season, Occasion occasion, Double humidityPercent) {
        String climate = switch (season) {
            case HOT -> (humidityPercent != null && humidityPercent >= 70) ? "Nefes Alabilir" : "Serin";
            case MILD -> "Dengeli";
            case COOL -> "Katmanli";
            case COLD -> "Sicak Tutan";
        };
        String social = switch (occasion) {
            case MEETING -> "Resmi";
            case CASUAL -> "Rahat";
            case SPORT -> "Aktif";
        };
        return climate + " & " + social;
    }

    private String composeSummary(
            SeasonBand season,
            Occasion occasion,
            double temperatureCelsius,
            Optional<ScoredPick> top,
            Optional<ScoredPick> bottom,
            Optional<ScoredPick> accessory,
            ColorHarmony.Result colorHarmony) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT,
                "%.0f°C (%s) ve %s baglami icin ",
                temperatureCelsius, season.label(), occasion.label()));
        if (top.isEmpty() && bottom.isEmpty() && accessory.isEmpty()) {
            sb.append("dolaptan uygun kombin kurulamadi.");
            return sb.toString();
        }
        sb.append("onerilen kombin: ");
        List<String> parts = new ArrayList<>();
        top.ifPresent(p -> parts.add(p.item().getCategory()));
        bottom.ifPresent(p -> parts.add(p.item().getCategory()));
        accessory.ifPresent(p -> parts.add(p.item().getCategory()));
        sb.append(String.join(" + ", parts));
        sb.append(" (").append(colorHarmony.type().label()).append(" renk uyumu).");
        return sb.toString();
    }

    private static double averageScore(
            Optional<ScoredPick> top,
            Optional<ScoredPick> bottom,
            Optional<ScoredPick> accessory) {
        double sum = 0;
        int count = 0;
        for (Optional<ScoredPick> pick : List.of(top, bottom, accessory)) {
            if (pick.isPresent()) {
                sum += pick.get().score();
                count++;
            }
        }
        return count == 0 ? 0.0 : round2(sum / count);
    }

    private static boolean isDress(WardrobeItem item) {
        return "dress".equalsIgnoreCase(item.getCategory());
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
