package app.aura.backend.service;

import app.aura.backend.config.AuthProperties;
import app.aura.backend.model.RefreshToken;
import app.aura.backend.model.User;
import app.aura.backend.repository.RefreshTokenRepository;
import app.aura.backend.web.UnauthorizedException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Refresh token uretim, hash saklama ve rotasyon (theft detection).
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthProperties authProperties;
    private final TransactionTemplate requiresNew;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            AuthProperties authProperties,
            PlatformTransactionManager transactionManager) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.authProperties = authProperties;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public record IssuedRefresh(String rawToken, RefreshToken entity) {
    }

    @Transactional
    public IssuedRefresh issue(User user) {
        return issue(user, null);
    }

    @Transactional
    public IssuedRefresh issue(User user, String deviceInfo) {
        String raw = generateRawToken();
        Instant expiresAt = Instant.now()
                .plusSeconds(authProperties.refreshExpirationDays() * 86_400L);
        String device = deviceInfo == null || deviceInfo.isBlank() ? null : deviceInfo.trim();
        RefreshToken entity = new RefreshToken(user, sha256Hex(raw), expiresAt, device);
        refreshTokenRepository.save(entity);
        return new IssuedRefresh(raw, entity);
    }

    /**
     * Gecerli tokeni dondurur; revoked (replay) ise kullanicinin tum tokenlerini iptal eder.
     */
    @Transactional
    public RefreshToken requireUsable(String rawToken) {
        String hash = sha256Hex(rawToken);
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new UnauthorizedException("Gecersiz refresh token."));

        if (token.isRevoked()) {
            Long userId = token.getUser().getId();
            requiresNew.executeWithoutResult(status -> refreshTokenRepository.revokeAllActiveForUser(userId));
            log.warn("Refresh token replay / hirsizlik: userId={} tum oturumlar iptal", userId);
            throw new UnauthorizedException(
                    "Refresh token yeniden kullanildi; tum oturumlar sonlandirildi.");
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            token.revoke();
            refreshTokenRepository.save(token);
            throw new UnauthorizedException("Refresh token suresi dolmus.");
        }
        return token;
    }

    @Transactional
    public IssuedRefresh rotate(String rawToken) {
        RefreshToken current = requireUsable(rawToken);
        current.revoke();
        refreshTokenRepository.save(current);
        return issue(current.getUser(), current.getDeviceInfo());
    }

    @Transactional
    public void revokeRawToken(String rawToken) {
        String hash = sha256Hex(rawToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            if (!token.isRevoked()) {
                token.revoke();
                refreshTokenRepository.save(token);
            }
        });
    }

    @Transactional
    public void revokeRawTokenForUser(String rawToken, Long userId) {
        String hash = sha256Hex(rawToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            if (token.getUser().getId().equals(userId) && !token.isRevoked()) {
                token.revoke();
                refreshTokenRepository.save(token);
            }
        });
    }

    public static String generateRawToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 kullanilamiyor", exception);
        }
    }
}
