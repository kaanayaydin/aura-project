package app.aura.backend.service;

import app.aura.backend.config.ChatProperties;
import app.aura.backend.dto.ChatAuraRequest;
import app.aura.backend.dto.ChatAuraResponse;
import app.aura.backend.dto.ChatTurn;
import app.aura.backend.dto.WeatherSnapshot;
import app.aura.backend.engine.AuraStylistPrompt;
import app.aura.backend.engine.WardrobeGuardrail;
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

    private final ChatProperties chatProperties;
    private final WardrobeItemRepository wardrobeItemRepository;
    private final UserPerfumeRepository userPerfumeRepository;
    private final UserRepository userRepository;
    private final WeatherService weatherService;
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

        String systemPrompt = AuraStylistPrompt.build(wardrobe, shelf, weather);
        String weatherSummary = "%.0f°C, %s, %s (%s)".formatted(
                weather.temperatureCelsius(),
                weather.condition(),
                weather.locationName(),
                weather.source());

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
            history.stream()
                    .filter(turn -> turn != null && turn.role() != null && turn.content() != null)
                    .filter(turn -> {
                        String role = turn.role().trim().toLowerCase(Locale.ROOT);
                        return role.equals("user") || role.equals("assistant");
                    })
                    .limit(MAX_HISTORY)
                    .forEach(turn -> messages.add(Map.of(
                            "role", turn.role().trim().toLowerCase(Locale.ROOT),
                            "content", turn.content().trim())));
        }
        messages.add(Map.of("role", "user", "content", userMessage.trim()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", chatProperties.model());
        body.put("messages", messages);
        body.put("stream", false);
        body.put("options", Map.of("temperature", chatProperties.temperature()));

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

    /** Ollama yokken kapali liste + saf Turkce markdown yanit. */
    private String buildFallbackReply(
            String message,
            List<WardrobeItem> wardrobe,
            List<UserPerfume> shelf,
            WeatherSnapshot weather) {
        String lower = message.toLowerCase(Locale.ROOT);
        String scene = "%.0f°C, %s — %s".formatted(
                weather.temperatureCelsius(),
                weather.condition(),
                weather.locationName());

        if (wardrobe.isEmpty()) {
            return """
                    Bugün sahne **%s**.

                    Dolap listesi boş — kombin uydurmuyorum. Birkaç gerçek parça ekle; \
                    sonra yalnızca onlarla net bir imaj çizerim.

                    _Aura notu: önce envanter, sonra stil._
                    """.formatted(scene).strip();
        }

        List<String> pieces = wardrobe.stream()
                .limit(4)
                .map(AuraStylistPrompt::describePiece)
                .toList();
        String pieceLine = pieces.stream()
                .map(piece -> "**" + piece + "**")
                .collect(Collectors.joining(" + "));

        String perfumeLine = shelf.isEmpty()
                ? "Parfüm önermiyorum; raf listesi boş."
                : "Koku: **" + AuraStylistPrompt.describePerfume(shelf.getFirst()) + "**.";

        String climateTip;
        if (weather.temperatureCelsius() >= 24) {
            climateTip = "Hava sıcak — listedeki hafif üstlerle kal.";
        } else if (weather.temperatureCelsius() <= 10) {
            climateTip = "Hava serin — listedeki katmanlı üstleri tercih et.";
        } else {
            climateTip = "Ilık sahne — listedeki dengeli bir üst-alt yeterli.";
        }

        if (lower.contains("parfum") || lower.contains("parfüm") || lower.contains("koku")
                || lower.contains("sise") || lower.contains("şişe")) {
            return """
                    Bugün sahne **%s**.

                    %s
                    %s

                    _Aura notu: koku, listedeki kombinle aynı cümlede bitsin._
                    """.formatted(scene, perfumeLine, climateTip).strip();
        }

        return """
                Bugün sahne **%s**.

                Kombin (yalnızca dolap listesinden): %s.
                %s
                %s

                _Aura notu: listede yoksa yok — uydurma yok._
                """.formatted(scene, pieceLine, climateTip, perfumeLine).strip();
    }
}
