package app.aura.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * VTON orkestrasyon ayarlari — yerel FastAPI veya RunPod Serverless.
 *
 * @param mockWorkerEnabled        true ise yerel MockVtonWorkerClient (test)
 * @param mockAutoAdvanceOnPoll    status sorgusunda yerel QUEUED→PROCESSING→COMPLETED
 * @param provider                 local | runpod (varsayilan: local)
 * @param workerBaseUrl            Yerel FastAPI veya RunPod endpoint base
 *                                 (ornek: https://api.runpod.ai/v2/{ENDPOINT_ID})
 * @param apiKey                   RunPod Bearer anahtari (AURA_RUNPOD_API_KEY)
 * @param runPath                  Is baslatma yolu (varsayilan /run)
 * @param statusPathTemplate       Durum yolu sablonu ({id} yer tutucu)
 * @param connectTimeoutSeconds       TCP baglanti zaman asimi
 * @param inferenceTimeoutSeconds     Normal GPU inference butcesi (saniye)
 * @param coldStartAllowanceSeconds   Uyuyan worker icin ek bekleme (saniye)
 * @param readTimeoutSeconds          enqueue okuma; 0 ise inference + cold-start
 * @param statusReadTimeoutSeconds    status poll okuma zaman asimi
 * @param coldStartRetryEnabled       5xx/timeout sonrasi 1 retry
 * @param coldStartRetryDelayMs       Retry oncesi bekleme (ms)
 * @param dailyLimit               Kullanici basina gunluk VTON kotasi (varsayilan 5)
 */
@ConfigurationProperties(prefix = "aura.vton")
public record VtonProperties(
        boolean mockWorkerEnabled,
        boolean mockAutoAdvanceOnPoll,
        String provider,
        String workerBaseUrl,
        String apiKey,
        String runPath,
        String statusPathTemplate,
        int connectTimeoutSeconds,
        int inferenceTimeoutSeconds,
        int coldStartAllowanceSeconds,
        int readTimeoutSeconds,
        int statusReadTimeoutSeconds,
        Boolean coldStartRetryEnabled,
        Long coldStartRetryDelayMs,
        int dailyLimit) {

    public VtonProperties {
        if (provider == null || provider.isBlank()) {
            provider = "local";
        } else {
            provider = provider.trim().toLowerCase();
        }
        if (workerBaseUrl == null || workerBaseUrl.isBlank()) {
            workerBaseUrl = "http://127.0.0.1:8001";
        }
        if (apiKey == null) {
            apiKey = "";
        }
        if (runPath == null || runPath.isBlank()) {
            runPath = "/run";
        }
        if (statusPathTemplate == null || statusPathTemplate.isBlank()) {
            statusPathTemplate = "/status/{id}";
        }
        if (connectTimeoutSeconds <= 0) {
            connectTimeoutSeconds = 10;
        }
        if (inferenceTimeoutSeconds <= 0) {
            inferenceTimeoutSeconds = 90;
        }
        if (coldStartAllowanceSeconds <= 0) {
            coldStartAllowanceSeconds = 30;
        }
        if (readTimeoutSeconds <= 0) {
            readTimeoutSeconds = inferenceTimeoutSeconds + coldStartAllowanceSeconds;
        }
        if (statusReadTimeoutSeconds <= 0) {
            statusReadTimeoutSeconds = 30;
        }
        if (coldStartRetryEnabled == null) {
            coldStartRetryEnabled = Boolean.TRUE;
        }
        if (coldStartRetryDelayMs == null || coldStartRetryDelayMs <= 0) {
            coldStartRetryDelayMs = 2000L;
        }
        if (dailyLimit <= 0) {
            dailyLimit = 5;
        }
    }

    public boolean isRunPod() {
        return "runpod".equalsIgnoreCase(provider);
    }

    public boolean isLocal() {
        return !isRunPod();
    }
}
