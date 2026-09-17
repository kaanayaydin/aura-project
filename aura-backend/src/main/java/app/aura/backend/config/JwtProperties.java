package app.aura.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT imza ve omur ayarlari.
 *
 * @param secret              HS256 anahtari (uretimde env ile verilir; min 32 karakter)
 * @param expirationMinutes   access token suresi (varsayilan 15 dk)
 * @param issuer              token iss claim
 */
@ConfigurationProperties(prefix = "aura.security.jwt")
public record JwtProperties(
        String secret,
        long expirationMinutes,
        String issuer) {

    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            secret = "aura-dev-jwt-secret-change-me-32chars!!";
        }
        if (secret.length() < 32) {
            secret = (secret + "aura-dev-jwt-secret-padding-32+").substring(0, 32);
        }
        if (expirationMinutes <= 0) {
            expirationMinutes = 15;
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "aura-backend";
        }
    }

    public long expirationSeconds() {
        return expirationMinutes * 60;
    }
}
