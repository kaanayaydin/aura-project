package app.aura.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * aura-vision HTTP istemci ayarlari (garment normalize vb.).
 */
@ConfigurationProperties(prefix = "aura.vision")
public record VisionProperties(
        String baseUrl,
        boolean normalizeGarmentEnabled,
        int connectTimeoutSeconds,
        int readTimeoutSeconds) {

    public VisionProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://127.0.0.1:8000";
        }
        if (connectTimeoutSeconds <= 0) {
            connectTimeoutSeconds = 5;
        }
        if (readTimeoutSeconds <= 0) {
            readTimeoutSeconds = 60;
        }
    }

    public String normalizeGarmentUrl() {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return base + "/api/v1/vision/normalize-garment";
    }
}
