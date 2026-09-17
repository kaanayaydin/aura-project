package app.aura.backend.service;

import app.aura.backend.dto.AuraSuggestionResponse;
import app.aura.backend.dto.AuraSuggestionResponse.ColorHarmonyInfo;
import app.aura.backend.dto.AuraSuggestionResponse.ContextSnapshot;
import app.aura.backend.dto.AuraSuggestionResponse.PerfumeRecommendation;
import app.aura.backend.dto.AuraSuggestionResponse.SuggestedPiece;
import app.aura.backend.dto.SuggestAuraRequest;
import app.aura.backend.dto.WardrobeItemResponse;
import app.aura.backend.dto.WeatherSnapshot;
import app.aura.backend.engine.GarmentSlot;
import app.aura.backend.engine.Occasion;
import app.aura.backend.engine.OutfitRuleEngine;
import app.aura.backend.engine.OutfitRuleEngine.OutfitPlan;
import app.aura.backend.engine.OutfitRuleEngine.ScoredPick;
import app.aura.backend.engine.PerfumeRuleEngine;
import app.aura.backend.engine.PerfumeRuleEngine.PerfumePick;
import app.aura.backend.model.WardrobeItem;
import app.aura.backend.repository.UserRepository;
import app.aura.backend.repository.WardrobeItemRepository;
import app.aura.backend.web.EmptyWardrobeException;
import app.aura.backend.web.InvalidOccasionException;
import app.aura.backend.web.UserNotFoundException;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Baglam ve Termodinamik Motoru — orkestrasyon katmani.
 *
 * Kimlik JWT authenticatedUserId ile gelir; yalnizca o kullanicinin dolabi kullanilir.
 */
@Service
public class AuraService {

    private static final Logger log = LoggerFactory.getLogger(AuraService.class);

    private final WardrobeItemRepository wardrobeItemRepository;
    private final UserRepository userRepository;
    private final WeatherService weatherService;
    private final OutfitRuleEngine outfitEngine;
    private final PerfumeRuleEngine perfumeEngine;

    public AuraService(
            WardrobeItemRepository wardrobeItemRepository,
            UserRepository userRepository,
            WeatherService weatherService) {
        this.wardrobeItemRepository = wardrobeItemRepository;
        this.userRepository = userRepository;
        this.weatherService = weatherService;
        this.outfitEngine = new OutfitRuleEngine();
        this.perfumeEngine = new PerfumeRuleEngine();
    }

    @Transactional(readOnly = true)
    public AuraSuggestionResponse suggest(Long authenticatedUserId, SuggestAuraRequest request) {
        Occasion occasion = Occasion.tryParse(request.occasion())
                .orElseThrow(() -> new InvalidOccasionException(
                        "Desteklenen occasion degerleri: meeting, casual, sport. Gelen: "
                                + request.occasion()));

        if (!userRepository.existsById(authenticatedUserId)) {
            throw new UserNotFoundException(
                    "Kullanici bulunamadi: %d".formatted(authenticatedUserId));
        }

        List<WardrobeItem> wardrobe = wardrobeItemRepository.findByUserId(authenticatedUserId);
        if (wardrobe.isEmpty()) {
            throw new EmptyWardrobeException(
                    "Kullanici %d icin dolap bos; oneri uretilemiyor.".formatted(authenticatedUserId));
        }

        boolean usable = wardrobe.stream()
                .anyMatch(item -> GarmentSlot.ofCategory(item.getCategory()) != GarmentSlot.IGNORED);
        if (!usable) {
            throw new EmptyWardrobeException(
                    "Dolapta giyilebilir parca yok (yalnizca parfum vb. kayitlar var).");
        }

        ResolvedWeather weather = resolveWeather(request);

        OutfitPlan plan = outfitEngine.suggest(
                wardrobe,
                weather.temperatureCelsius(),
                weather.humidityPercent(),
                occasion);

        PerfumePick perfume = perfumeEngine.recommend(
                plan.seasonBand(),
                occasion,
                weather.humidityPercent(),
                plan.vibe());

        boolean includeImages = Boolean.TRUE.equals(request.includeImages());
        AuraSuggestionResponse response = toResponse(
                authenticatedUserId, weather, plan, perfume, includeImages, plan.occasion().label());

        log.info(
                "Aura oneri: kullanici={} vibe='{}' skor={} hava={}°C/{}% ({}) ust={} alt={} aksesuar={} parfum={}/{}",
                authenticatedUserId,
                response.vibe(),
                response.matchScore(),
                weather.temperatureCelsius(),
                weather.humidityPercent(),
                weather.source(),
                slotCategory(plan.top()),
                slotCategory(plan.bottom()),
                slotCategory(plan.accessory()),
                perfume.perfume().brand(),
                perfume.perfume().name());

        return response;
    }

    private ResolvedWeather resolveWeather(SuggestAuraRequest request) {
        boolean auto = Boolean.TRUE.equals(request.useAutoWeather())
                || request.temperatureCelsius() == null;
        if (!auto) {
            return new ResolvedWeather(
                    request.temperatureCelsius(),
                    request.humidityPercent() != null ? request.humidityPercent() : 55.0,
                    "manual",
                    "Manual",
                    "manual",
                    false);
        }
        WeatherSnapshot snapshot = weatherService.current(request.latitude(), request.longitude());
        return new ResolvedWeather(
                snapshot.temperatureCelsius(),
                snapshot.humidityPercent(),
                snapshot.source(),
                snapshot.condition(),
                snapshot.locationName(),
                true);
    }

    private AuraSuggestionResponse toResponse(
            Long userId,
            ResolvedWeather weather,
            OutfitPlan plan,
            PerfumePick perfume,
            boolean includeImages,
            String occasionLabel) {
        return new AuraSuggestionResponse(
                userId,
                plan.vibe(),
                plan.summary(),
                new ContextSnapshot(
                        weather.temperatureCelsius(),
                        weather.humidityPercent(),
                        occasionLabel,
                        plan.seasonBand().label(),
                        weather.source(),
                        weather.condition(),
                        weather.locationName(),
                        weather.autoWeather()),
                toPiece("top", plan.top(), includeImages),
                toPiece("bottom", plan.bottom(), includeImages),
                toPiece("accessory", plan.accessory(), includeImages),
                PerfumeRecommendation.from(perfume),
                ColorHarmonyInfo.from(plan.colorHarmony()),
                plan.matchScore(),
                plan.notes());
    }

    private SuggestedPiece toPiece(String role, Optional<ScoredPick> pick, boolean includeImages) {
        if (pick.isEmpty()) {
            return null;
        }
        ScoredPick scored = pick.get();
        WardrobeItemResponse item = includeImages
                ? WardrobeItemResponse.withImage(scored.item())
                : WardrobeItemResponse.withoutImage(scored.item());
        return new SuggestedPiece(role, item, round2(scored.score()), scored.reason());
    }

    private static String slotCategory(Optional<ScoredPick> pick) {
        return pick.map(p -> p.item().getCategory()).orElse("-");
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record ResolvedWeather(
            double temperatureCelsius,
            double humidityPercent,
            String source,
            String condition,
            String locationName,
            boolean autoWeather) {
    }
}
