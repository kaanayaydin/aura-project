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
            throw new IllegalStateException(
                    "AURA_JWT_SECRET zorunlu. Varsayilan secret yok; env set edilmeden uygulama baslamaz.");
        }
        if (secret.length() < 32) {
            throw new IllegalStateException("AURA_JWT_SECRET en az 32 karakter olmali.");
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
