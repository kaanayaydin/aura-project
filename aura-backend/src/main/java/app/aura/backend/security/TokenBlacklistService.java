package app.aura.backend.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Logout sonrasi access JWT invalidasyonu — bellek ici, token omrune kadar.
 * Ham token saklanmaz; SHA-256 hash kullanilir.
 */
@Service
public class TokenBlacklistService {

    private static final Logger log = LoggerFactory.getLogger(TokenBlacklistService.class);

    private final ConcurrentHashMap<String, Instant> entries = new ConcurrentHashMap<>();

    public void blacklistUntil(String rawAccessToken, Instant expiresAt) {
        if (rawAccessToken == null || rawAccessToken.isBlank() || expiresAt == null) {
            return;
        }
        Instant now = Instant.now();
        if (!expiresAt.isAfter(now)) {
            return;
        }
        entries.put(sha256Hex(rawAccessToken), expiresAt);
        purgeExpired(now);
        log.debug("Access token blacklist'e alindi; expiresAt={}", expiresAt);
    }

    public boolean isBlacklisted(String rawAccessToken) {
        if (rawAccessToken == null || rawAccessToken.isBlank()) {
            return false;
        }
        String hash = sha256Hex(rawAccessToken);
        Instant expiresAt = entries.get(hash);
        if (expiresAt == null) {
            return false;
        }
        if (!expiresAt.isAfter(Instant.now())) {
            entries.remove(hash, expiresAt);
            return false;
        }
        return true;
    }

    int size() {
        return entries.size();
    }

    private void purgeExpired(Instant now) {
        Iterator<Map.Entry<String, Instant>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Instant> entry = it.next();
            if (!entry.getValue().isAfter(now)) {
                it.remove();
            }
        }
    }

    private static String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 kullanilamiyor", exception);
        }
    }
}
