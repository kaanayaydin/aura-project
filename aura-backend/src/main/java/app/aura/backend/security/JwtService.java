package app.aura.backend.security;

import app.aura.backend.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * HS256 JWT uretim / dogrulama.
 */
@Service
public class JwtService {

    public static final String CLAIM_USER_ID = "uid";

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String issueToken(Long userId, String username) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(properties.expirationMinutes() * 60);
        return Jwts.builder()
                .issuer(properties.issuer())
                .subject(username)
                .claim(CLAIM_USER_ID, userId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
    }

    public AuraPrincipal parse(String token) {
        try {
            Claims claims = parseClaims(token);
            Object uid = claims.get(CLAIM_USER_ID);
            if (uid == null) {
                throw new JwtException("uid claim eksik");
            }
            long userId = uid instanceof Number number
                    ? number.longValue()
                    : Long.parseLong(uid.toString());
            String username = claims.getSubject();
            if (username == null || username.isBlank()) {
                throw new JwtException("subject eksik");
            }
            return new AuraPrincipal(userId, username);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new JwtAuthenticationException("Gecersiz veya suresi dolmus token", exception);
        }
    }

    /** Token expiration claim; blacklist omru icin. */
    public Instant expirationOf(String token) {
        try {
            Date exp = parseClaims(token).getExpiration();
            if (exp == null) {
                return Instant.now().plusSeconds(expirationSeconds());
            }
            return exp.toInstant();
        } catch (JwtException | IllegalArgumentException exception) {
            throw new JwtAuthenticationException("Gecersiz veya suresi dolmus token", exception);
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(properties.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long expirationMinutes() {
        return properties.expirationMinutes();
    }

    public long expirationSeconds() {
        return properties.expirationSeconds();
    }
}
