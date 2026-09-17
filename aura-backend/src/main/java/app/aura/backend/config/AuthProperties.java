package app.aura.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Gercek auth ayarlari (lock, rate-limit, refresh omru).
 */
@ConfigurationProperties(prefix = "aura.security.auth")
public record AuthProperties(
        int maxFailedAttempts,
        int lockDurationMinutes,
        int rateLimitPerMinute,
        int refreshExpirationDays) {

    public AuthProperties {
        if (maxFailedAttempts <= 0) {
            maxFailedAttempts = 5;
        }
        if (lockDurationMinutes <= 0) {
            lockDurationMinutes = 15;
        }
        if (rateLimitPerMinute <= 0) {
            rateLimitPerMinute = 10;
        }
        if (refreshExpirationDays <= 0) {
            refreshExpirationDays = 7;
        }
    }
}
