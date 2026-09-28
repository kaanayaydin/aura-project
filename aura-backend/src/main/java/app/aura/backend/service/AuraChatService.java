package app.aura.backend.service;

import app.aura.backend.config.ChatProperties;
import app.aura.backend.dto.ChatAuraRequest;
import app.aura.backend.dto.ChatAuraResponse;
import app.aura.backend.dto.ChatTurn;
import app.aura.backend.dto.WeatherSnapshot;
import app.aura.backend.engine.AuraStylistPrompt;
import app.aura.backend.engine.Occasion;
import app.aura.backend.engine.OutfitRuleEngine;
import app.aura.backend.engine.WardrobeGuardrail;
import app.aura.backend.engine.WeatherDisplay;
import app.aura.backend.model.UserPerfume;
import app.aura.backend.model.WardrobeItem;
import app.aura.backend.repository.UserPerfumeRepository;
import app.aura.backend.repository.UserRepository;
import app.aura.backend.repository.WardrobeItemRepository;
import app.aura.backend.web.ChatUnavailableException;
import app.aura.backend.web.UserNotFoundException;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

/**
 * Lokal LLM (Ollama) destekli Aura stilist asistani.
 *
 * Kimlik JWT authenticatedUserId ile gelir; guardrail yalnizca o kullanicinin
 * dolap + parfum rafi + hava baglamini kullanir.
 */
@Service
public class AuraChatService {

    private static final Logger log = LoggerFactory.getLogger(AuraChatService.class);
    private static final int MAX_HISTORY = 12;

    /**
     * Sohbet uzayınca modele giden turlar: sıra korunur, yalnızca son {@link #MAX_HISTORY}.
     */
    static List<ChatTurn> selectRecentHistory(List<ChatTurn> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        List<ChatTurn> accepted = new ArrayList<>();
        for (ChatTurn turn : history) {
            if (turn == null || turn.role() == null || turn.content() == null) {
                continue;
            }
            String role = turn.role().trim().toLowerCase(Locale.ROOT);
            if (!role.equals("user") && !role.equals("assistant")) {
                continue;
            }
            String content = turn.content().trim();
            if (content.isEmpty()) {
                continue;
            }
            accepted.add(new ChatTurn(role, content));
        }
        int from = Math.max(0, accepted.size() - MAX_HISTORY);
        return List.copyOf(accepted.subList(from, accepted.size()));
    }

    private final ChatProperties chatProperties;
    private final WardrobeItemRepository wardrobeItemRepository;
    private final UserPerfumeRepository userPerfumeRepository;
    private final UserRepository userRepository;
    private final WeatherService weatherService;
    private final OutfitRuleEngine outfitEngine;
    private final RestClient ollamaClient;

    public AuraChatService(
            ChatProperties chatProperties,
            WardrobeItemRepository wardrobeItemRepository,
            UserPerfumeRepository userPerfumeRepository,
            UserRepository userRepository,
            WeatherService weatherService) {
        this.chatProperties = chatProperties;
        this.wardrobeItemRepository = wardrobeItemRepository;
        this.userPerfumeRepository = userPerfumeRepository;
        this.userRepository = userRepository;
        this.weatherService = weatherService;
        this.outfitEngine = new OutfitRuleEngine();

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(chatProperties.timeoutSeconds()));
        this.ollamaClient = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(chatProperties.ollamaBaseUrl())
                .build();
    }

    @Transactional(readOnly = true)
    public ChatAuraResponse chat(Long authenticatedUserId, ChatAuraRequest request) {
        if (!userRepository.existsById(authenticatedUserId)) {
            throw new UserNotFoundException(
                    "Kullanici bulunamadi: %d".formatted(authenticatedUserId));
        }
        // Guardrail yalnizca authenticated kullanicinin dolap + raf profilini gorur.
        List<WardrobeItem> wardrobe = wardrobeItemRepository.findByUserId(authenticatedUserId);
        List<UserPerfume> shelf =
                userPerfumeRepository.findByUserIdOrderByBrandAscNameAsc(authenticatedUserId);
        WeatherSnapshot weather = weatherService.current(request.latitude(), request.longitude());

        String systemPrompt = AuraStylistPrompt.build(
                wardrobe, shelf, weather, request.message());
        String weatherSummary = WeatherDisplay.summary(weather);

        try {
            String reply = callOllama(systemPrompt, request.message(), request.history());
            String guarded = applyGuardrail(reply, wardrobe, shelf, weather);
            return new ChatAuraResponse(
                    guarded,
                    chatProperties.model(),
                    "ollama",
                    wardrobe.size(),
                    shelf.size(),
                    weatherSummary);
        } catch (Exception exception) {
            log.warn("Ollama yanit uretemedi: {}", exception.getMessage());
            if (!chatProperties.fallbackEnabled()) {
                throw new ChatUnavailableException(
                        "Lokal LLM (Ollama) erisilemiyor. Ollama'yi baslatin: "
                                + chatProperties.ollamaBaseUrl(),
                        exception);
            }
            String fallback = buildFallbackReply(request.message(), wardrobe, shelf, weather);
            String guarded = applyGuardrail(fallback, wardrobe, shelf, weather);
            return new ChatAuraResponse(
                    guarded,
                    "aura-fallback",
                    "fallback",
                    wardrobe.size(),
                    shelf.size(),
                    weatherSummary);
        }
    }

    private String applyGuardrail(
            String reply,
            List<WardrobeItem> wardrobe,
            List<UserPerfume> shelf,
            WeatherSnapshot weather) {
        var result = WardrobeGuardrail.filter(reply, wardrobe, shelf, weather);
        if (result.mutated()) {
            log.info(
                    "Wardrobe guardrail uygulandi: droppedLines={} replyLen {} -> {}",
                    result.droppedLines(),
                    reply == null ? 0 : reply.length(),
                    result.reply().length());
        }
        return result.reply();
    }

    private String callOllama(String systemPrompt, String userMessage, List<ChatTurn> history) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));

        if (history != null) {
            for (ChatTurn turn : selectRecentHistory(history)) {
                messages.add(Map.of(
                        "role", turn.role(),
                        "content", turn.content()));
            }
        }
        messages.add(Map.of("role", "user", "content", userMessage.trim()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", chatProperties.model());
        body.put("messages", messages);
        body.put("stream", false);
        body.put("options", Map.of(
                "temperature", chatProperties.temperature(),
                "num_ctx", chatProperties.numCtx()));

        JsonNode root = ollamaClient.post()
                .uri("/api/chat")
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        if (root == null || !root.has("message")) {
            throw new IllegalStateException("Ollama cevabi bos.");
        }
        JsonNode message = root.get("message");
        String content = message.path("content").asText(null);
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("Ollama mesaj icerigi bos.");
        }
        return content;
    }

    /**
     * Ollama yokken kapali liste + saf Turkce markdown yanit.
     * Paket görünür: sıcaklığı sabitleyen birim test aynı paketten çağırır.
     */
    String buildFallbackReply(
            String message,
            List<WardrobeItem> wardrobe,
            List<UserPerfume> shelf,
            WeatherSnapshot weather) {
        String lower = message.toLowerCase(Locale.ROOT);
        String opening = "Bugün hava " + WeatherDisplay.summary(weather) + ".";

        if (wardrobe.isEmpty()) {
            return """
                    %s

                    Dolabın henüz boş. Birkaç parça eklersen sana onlarla bir kombin hazırlarım.

                    _Aura notu: önce dolap, sonra stil._
                    """.formatted(opening).strip();
        }

        String pieceLine = fallbackCombination(wardrobe, weather);

        String perfumeLine = shelf.isEmpty()
                ? WardrobeGuardrail.EMPTY_SHELF_LINE
                : "Koku: **" + AuraStylistPrompt.describePerfume(shelf.getFirst()) + "**.";

        String climateTip;
        if (weather.temperatureCelsius() >= 24) {
            climateTip = "Hava sıcak; ince ve hafif parçalarla kal.";
        } else if (weather.temperatureCelsius() <= 10) {
            climateTip = "Hava serin; kat kat giyin.";
        } else {
            climateTip = "Hava ılık; bir üst ve bir alt yeter.";
        }

        if (lower.contains("parfum") || lower.contains("parfüm") || lower.contains("koku")
                || lower.contains("sise") || lower.contains("şişe")) {
            return """
                    %s

                    %s
                    %s

                    _Aura notu: koku kombini tamamlasın, bastırmasın._
                    """.formatted(opening, perfumeLine, climateTip).strip();
        }

        return """
                %s

                Kombin önerim: %s.
                %s
                %s

                _Aura notu: az parça, net çizgi._
                """.formatted(opening, pieceLine, climateTip, perfumeLine).strip();
    }

    /**
     * Üst, alt, aksesuar sırası. Motorda parça yoksa (yalnızca IGNORED kategori)
     * eski grup-başına-ilk-parça listesine düşer. Motorun not ve skor metinleri yok.
     */
    private String fallbackCombination(List<WardrobeItem> wardrobe, WeatherSnapshot weather) {
        OutfitRuleEngine.OutfitPlan plan = outfitEngine.suggest(
                wardrobe,
                weather.temperatureCelsius(),
                weather.humidityPercent(),
                Occasion.CASUAL);
        List<String> labels = new ArrayList<>();
        plan.top().ifPresent(pick -> labels.add(AuraStylistPrompt.describePiece(pick.item())));
        plan.bottom().ifPresent(pick -> labels.add(AuraStylistPrompt.describePiece(pick.item())));
        plan.accessory().ifPresent(pick -> labels.add(AuraStylistPrompt.describePiece(pick.item())));
        if (labels.isEmpty()) {
            labels.addAll(AuraStylistPrompt.outfitPieces(wardrobe, 4));
        }
        return labels.stream()
                .map(label -> "**" + label + "**")
                .collect(Collectors.joining(" + "));
    }
}
