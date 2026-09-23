package app.aura.backend.security;

import app.aura.backend.config.AuthProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "aura.security.auth.rate-limit-store", havingValue = "memory")
public class MemoryAuthRateLimitBuckets implements AuthRateLimitBuckets {

    private final AuthProperties authProperties;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public MemoryAuthRateLimitBuckets(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    @Override
    public boolean tryConsume(String ip) {
        return buckets.computeIfAbsent(ip, ignored -> newBucket()).tryConsume(1);
    }

    private Bucket newBucket() {
        int limit = authProperties.rateLimitPerMinute();
        Bandwidth bandwidth = Bandwidth.builder()
                .capacity(limit)
                .refillGreedy(limit, Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(bandwidth).build();
    }
}
