package app.aura.backend.service;

import app.aura.backend.config.VtonProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Python aura-vton FastAPI istemcisi (Redis/Celery kuyrugu).
 */
@Component
@ConditionalOnProperty(
        prefix = "aura.vton",
        name = "mock-worker-enabled",
        havingValue = "false",
        matchIfMissing = true)
@ConditionalOnProperty(
        prefix = "aura.vton",
        name = "provider",
        havingValue = "local",
        matchIfMissing = true)
public class HttpVtonWorkerClient implements VtonWorkerClient {

    private static final Logger log = LoggerFactory.getLogger(HttpVtonWorkerClient.class);

    private final RestClient restClient;
    private final RestClient statusRestClient;

    /**
     * Birden fazla constructor oldugu icin Spring'e hangi enjeksiyonu
     * kullanacagini acikca soyleriz (aksi halde no-arg arar ve patlar).
     */
    @Autowired
    public HttpVtonWorkerClient(VtonProperties properties) {
        this.restClient = buildClient(
                properties.workerBaseUrl(),
                properties.connectTimeoutSeconds(),
                properties.readTimeoutSeconds());
        this.statusRestClient = buildClient(
                properties.workerBaseUrl(),
                properties.connectTimeoutSeconds(),
                properties.statusReadTimeoutSeconds());
        log.info(
                "HttpVtonWorkerClient: baseUrl={} connect={}s read={}s statusRead={}s",
                properties.workerBaseUrl(),
                properties.connectTimeoutSeconds(),
                properties.readTimeoutSeconds(),
                properties.statusReadTimeoutSeconds());
    }

    /** Test / manuel kullanim icin. */
    HttpVtonWorkerClient(RestClient restClient) {
        this.restClient = restClient;
        this.statusRestClient = restClient;
    }

    private static RestClient buildClient(String baseUrl, int connectSeconds, int readSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(connectSeconds));
        factory.setReadTimeout(Duration.ofSeconds(readSeconds));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    @Override
    public String enqueue(EnqueuePayload payload) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jobId", payload.auraJobId());
        body.put("userId", payload.userId());
        body.put("wardrobeItemId", payload.wardrobeItemId());
        body.put("personImageBase64", payload.personImageBase64());
        body.put("garmentImageBase64", payload.garmentImageBase64());
        body.put("personImageUrl", payload.personImageUrl());
        body.put("garmentImageUrl", payload.garmentImageUrl());
        String clothType = payload.clothType();
        body.put("clothType", clothType == null || clothType.isBlank() ? "upper" : clothType);

        JsonNode root = restClient.post()
                .uri("/internal/vton/enqueue")
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        if (root == null || !root.has("workerJobId")) {
            throw new IllegalStateException("VTON worker enqueue cevabi bos.");
        }
        String workerJobId = root.get("workerJobId").asText();
        log.info(
                "VTON worker enqueue OK: auraJobId={} workerJobId={} status={} clothType={} garmentUrl={}",
                payload.auraJobId(),
                workerJobId,
                root.path("status").asText("QUEUED"),
                body.get("clothType"),
                payload.garmentImageUrl());
        return workerJobId;
    }

    @Override
    public WorkerStatusSnapshot status(String workerJobId) {
        JsonNode root = statusRestClient.get()
                .uri("/internal/vton/status/{jobId}", workerJobId)
                .retrieve()
                .body(JsonNode.class);
        if (root == null || !root.has("status")) {
            throw new IllegalStateException("VTON worker status cevabi bos: " + workerJobId);
        }
        return new WorkerStatusSnapshot(
                root.get("status").asText(),
                textOrNull(root, "resultImageUri"),
                textOrNull(root, "resultImageBase64"),
                textOrNull(root, "errorMessage"));
    }

    private static String textOrNull(JsonNode root, String field) {
        JsonNode node = root.path(field);
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText(null);
        return (value == null || value.isBlank()) ? null : value;
    }
}
