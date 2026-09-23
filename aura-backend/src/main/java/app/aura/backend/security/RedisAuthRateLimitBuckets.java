package app.aura.backend.security;

import app.aura.backend.config.AuthProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Bucket4j kovasi Redis'te. Baglanti yoksa uygulama bu bean'de durur.
 */
@Component
@ConditionalOnProperty(name = "aura.security.auth.rate-limit-store", havingValue = "redis", matchIfMissing = true)
public class RedisAuthRateLimitBuckets implements AuthRateLimitBuckets, DisposableBean {

    private final AuthProperties authProperties;
    private final RedisClient client;
    private final StatefulRedisConnection<byte[], byte[]> connection;
    private final ProxyManager<byte[]> proxyManager;

    public RedisAuthRateLimitBuckets(
            AuthProperties authProperties,
            @Value("${spring.data.redis.host:127.0.0.1}") String host,
            @Value("${spring.data.redis.port:6379}") int port) {
        this.authProperties = authProperties;
        this.client = RedisClient.create("redis://" + host + ":" + port);
        try {
            this.connection = client.connect(ByteArrayCodec.INSTANCE);
            this.connection.sync().ping();
        } catch (RuntimeException exception) {
            client.shutdown();
            throw new IllegalStateException(
                    "Auth rate-limit Redis'e baglanamadi (" + host + ":" + port + ").", exception);
        }
        this.proxyManager = LettuceBasedProxyManager.builderFor(connection).build();
    }

    @Override
    public boolean tryConsume(String ip) {
        byte[] key = ("aura:auth:rl:" + ip).getBytes(StandardCharsets.UTF_8);
        int limit = authProperties.rateLimitPerMinute();
        BucketConfiguration configuration = BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(limit)
                        .refillGreedy(limit, Duration.ofMinutes(1))
                        .build())
                .build();
        return proxyManager.builder().build(key, () -> configuration).tryConsume(1);
    }

    @Override
    public void destroy() {
        connection.close();
        client.shutdown();
    }
}
