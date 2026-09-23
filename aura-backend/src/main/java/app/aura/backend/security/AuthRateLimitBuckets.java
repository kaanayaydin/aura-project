package app.aura.backend.security;

/**
 * IP basina auth kova. Uretim Redis, test bellek.
 */
public interface AuthRateLimitBuckets {

    boolean tryConsume(String ip);
}
