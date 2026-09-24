package app.aura.backend.service;

import app.aura.backend.config.VtonProperties;
import app.aura.backend.web.VtonWorkerUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * RunPod Serverless VTON istemcisi — POST /run, GET /status/{id}, Bearer API key.
 * 5xx veya timeout: 1 retry, varsayilan 2 sn bekler. Ikinci hata
 * VtonWorkerUnavailableException — VtonService kotayi iade eder.
 * Okuma zamani asimi inference + cold-start payidir.
 */
@Component
@ConditionalOnProperty(
        prefix = "aura.vton",
        name = "mock-worker-enabled",
        havingValue = "false",
        matchIfMissing = true)
@ConditionalOnProperty(prefix = "aura.vton", name = "provider", havingValue = "runpod")
public class RunPodVtonWorkerClient implements VtonWorkerClient {

    private static final Logger log = LoggerFactory.getLogger(RunPodVtonWorkerClient.class);

    private final RestClient restClient;
    private final RestClient statusRestClient;
    private final String apiKey;
    private final String runPath;
    private final String statusPathTemplate;
    private final boolean coldStartRetryEnabled;
    private final long coldStartRetryDelayMs;
    private final BackoffSleeper sleeper;

    @Autowired
    public RunPodVtonWorkerClient(VtonProperties properties) {
        this(
                buildClient(
                        properties.workerBaseUrl(),
                        properties.connectTimeoutSeconds(),
                        properties.readTimeoutSeconds()),
                buildClient(
                        properties.workerBaseUrl(),
                        properties.connectTimeoutSeconds(),
                        properties.statusReadTimeoutSeconds()),
                properties,
                BackoffSleeper.THREAD);
        log.info(
                "RunPodVtonWorkerClient: baseUrl={} runPath={} statusPath={} retry={} delayMs={}",
                properties.workerBaseUrl(),
                properties.runPath(),
                properties.statusPathTemplate(),
                properties.coldStartRetryEnabled(),
                properties.coldStartRetryDelayMs());
    }

    /** Test / manuel kullanim. */
    RunPodVtonWorkerClient(
            RestClient restClient,
            VtonProperties properties,
            BackoffSleeper sleeper) {
        this(restClient, restClient, properties, sleeper);
    }

    private RunPodVtonWorkerClient(
            RestClient restClient,
            RestClient statusRestClient,
            VtonProperties properties,
            BackoffSleeper sleeper) {
        this.restClient = restClient;
        this.statusRestClient = statusRestClient;
        this.apiKey = properties.apiKey() == null ? "" : properties.apiKey();
        this.runPath = properties.runPath();
        this.statusPathTemplate = properties.statusPathTemplate();
        this.coldStartRetryEnabled = Boolean.TRUE.equals(properties.coldStartRetryEnabled());
        this.coldStartRetryDelayMs = properties.coldStartRetryDelayMs() == null
                ? 2000L
                : properties.coldStartRetryDelayMs();
        this.sleeper = sleeper == null ? BackoffSleeper.THREAD : sleeper;
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
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("jobId", payload.auraJobId());
        input.put("userId", payload.userId());
        input.put("wardrobeItemId", payload.wardrobeItemId());
        input.put("personImageBase64", payload.personImageBase64());
        input.put("garmentImageBase64", payload.garmentImageBase64());
        input.put("personImageUrl", payload.personImageUrl());
        input.put("garmentImageUrl", payload.garmentImageUrl());
        String clothType = payload.clothType();
        input.put("clothType", clothType == null || clothType.isBlank() ? "upper" : clothType);

        Map<String, Object> body = Map.of("input", input);

        JsonNode root = withColdStartRetry(
                "enqueue",
                () -> restClient.post()
                        .uri(runPath)
                        .contentType(MediaType.APPLICATION_JSON)
                        .headers(this::applyAuth)
                        .body(body)
                        .retrieve()
                        .body(JsonNode.class));

        if (root == null || !root.has("id") || root.get("id").asText().isBlank()) {
            throw new IllegalStateException("RunPod /run cevabi id icermiyor.");
        }
        String workerJobId = root.get("id").asText();
        log.info(
                "RunPod enqueue OK: auraJobId={} workerJobId={} status={} clothType={}",
                payload.auraJobId(),
                workerJobId,
                root.path("status").asText("IN_QUEUE"),
                input.get("clothType"));
        return workerJobId;
    }

    @Override
    public WorkerStatusSnapshot status(String workerJobId) {
        String path = statusPathTemplate.replace("{id}", workerJobId);
        JsonNode root = withColdStartRetry(
                "status",
                () -> statusRestClient.get()
                        .uri(path)
                        .headers(this::applyAuth)
                        .retrieve()
                        .body(JsonNode.class));

        if (root == null || !root.has("status")) {
            throw new IllegalStateException("RunPod status cevabi bos: " + workerJobId);
        }

        String runpodStatus = root.get("status").asText();
        String auraStatus = mapRunPodStatus(runpodStatus);
        JsonNode output = root.path("output");

        String resultUri = textOrNull(output, "resultImageUri");
        String resultB64 = textOrNull(output, "resultImageBase64");
        String errorMessage = textOrNull(output, "errorMessage");
        if (errorMessage == null) {
            errorMessage = textOrNull(root, "error");
        }
        // Handler dogrudan COMPLETED alanlarini da donebilir
        if (resultUri == null) {
            resultUri = textOrNull(root, "resultImageUri");
        }
        if (resultB64 == null) {
            resultB64 = textOrNull(root, "resultImageBase64");
        }

        return new WorkerStatusSnapshot(auraStatus, resultUri, resultB64, errorMessage);
    }

    private void applyAuth(HttpHeaders headers) {
        if (!apiKey.isBlank()) {
            headers.setBearerAuth(apiKey);
        }
    }

    private <T> T withColdStartRetry(String operation, Supplier<T> action) {
        try {
            return action.get();
        } catch (RuntimeException first) {
            if (!coldStartRetryEnabled || !isRetryable(first)) {
                throw wrapUnavailable(operation, first);
            }
            log.warn(
                    "RunPod {} cold-start retry ({} ms sonra): {}",
                    operation,
                    coldStartRetryDelayMs,
                    first.getMessage());
            sleepQuietly(coldStartRetryDelayMs);
            try {
                return action.get();
            } catch (RuntimeException second) {
                throw wrapUnavailable(operation, second);
            }
        }
    }

    private void sleepQuietly(long millis) {
        try {
            sleeper.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new VtonWorkerUnavailableException(
                    "VTON worker cold-start beklemesi kesildi.", interrupted);
        }
    }

    private static boolean isRetryable(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ResourceAccessException) {
                return true;
            }
            if (current instanceof RestClientResponseException response) {
                int code = response.getStatusCode().value();
                return code == 408 || code == 429 || code >= 500;
            }
            current = current.getCause();
        }
        return false;
    }

    private static VtonWorkerUnavailableException wrapUnavailable(
            String operation, Throwable cause) {
        if (cause instanceof VtonWorkerUnavailableException unavailable) {
            return unavailable;
        }
        String detail = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
        return new VtonWorkerUnavailableException(
                "VTON worker (%s) erisilemiyor: %s".formatted(operation, detail), cause);
    }

    static String mapRunPodStatus(String runpodStatus) {
        if (runpodStatus == null || runpodStatus.isBlank()) {
            return "QUEUED";
        }
        return switch (runpodStatus.trim().toUpperCase()) {
            case "IN_QUEUE", "QUEUED", "PENDING" -> "QUEUED";
            case "IN_PROGRESS", "PROCESSING", "STARTED", "RUNNING" -> "PROCESSING";
            case "COMPLETED", "SUCCESS" -> "COMPLETED";
            case "FAILED", "FAILURE", "CANCELLED", "CANCELED", "TIMED_OUT" -> "FAILED";
            default -> runpodStatus.trim().toUpperCase();
        };
    }

    private static String textOrNull(JsonNode root, String field) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return null;
        }
        JsonNode node = root.path(field);
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText(null);
        return (value == null || value.isBlank()) ? null : value;
    }
}
