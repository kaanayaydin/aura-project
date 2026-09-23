package app.aura.backend.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.aura.backend.config.StorageProperties;
import app.aura.backend.web.UnsafeObjectUrlException;
import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StorageUrlGuardTest {

    private StorageUrlGuard guard;

    @BeforeEach
    void setUp() {
        guard = new StorageUrlGuard(localMinio());
    }

    @Test
    void allowlistedMemoryObjectUrlIsAccepted() {
        guard.rejectUnsafeObjectUrl("http://127.0.0.1:9000/memory/aura-vton/person/abc.jpg");
    }

    @Test
    void allowlistedPathStyleBucketUrlIsAccepted() {
        guard.rejectUnsafeObjectUrl("http://127.0.0.1:9000/aura-vton/person/abc.jpg");
    }

    @Test
    void presignedQueryOnAllowlistedPathIsAccepted() {
        guard.rejectUnsafeObjectUrl(
                "http://127.0.0.1:9000/aura-vton/person/abc.jpg?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Expires=900");
    }

    @Test
    void metadataLinkLocalIsRejected() {
        assertBlocked("http://169.254.169.254/latest/meta-data/");
    }

    @Test
    void loopbackWorkerPortIsRejected() {
        assertBlocked("http://127.0.0.1:8001/internal");
    }

    @Test
    void documentationExternalIpBombIsRejected() {
        assertBlocked("http://203.0.113.1/bomb.jpg");
    }

    @Test
    void userinfoDoesNotBypassHostAllowlist() {
        assertBlocked("http://127.0.0.1:9000@169.254.169.254/latest/meta-data/");
    }

    @Test
    void pathTraversalIsRejected() {
        assertBlocked("http://127.0.0.1:9000/aura-vton/../aura-vton/person/x.jpg");
    }

    @Test
    void unknownBucketIsRejected() {
        assertBlocked("http://127.0.0.1:9000/not-a-bucket/person/x.jpg");
    }

    @Test
    void observeSamplesConstantIsTwo() throws Exception {
        org.assertj.core.api.Assertions.assertThat(StorageUrlGuard.OBSERVE_SAMPLES).isEqualTo(2);
        StorageUrlGuard guard = new StorageUrlGuard(localMinio());
        java.lang.reflect.Field field = StorageUrlGuard.class.getDeclaredField("observeSamples");
        field.setAccessible(true);
        org.assertj.core.api.Assertions.assertThat(field.getInt(guard)).isEqualTo(2);
    }

    @Test
    void persistentDnsHijackRejectedOnFirstRequest() throws Exception {
        InetAddress cdn = InetAddress.getByName("198.51.100.10");
        InetAddress hijack = InetAddress.getByName("169.254.169.254");
        AtomicInteger resolves = new AtomicInteger();
        StorageUrlGuard guard = new StorageUrlGuard(cdnStorage(), host -> {
            if (resolves.getAndIncrement() == 0) {
                return new InetAddress[] {cdn};
            }
            return new InetAddress[] {hijack};
        });
        assertThatThrownBy(
                        () -> guard.pin("http://files.aura.test:9000/aura-vton/person/x.jpg"))
                .isInstanceOf(UnsafeObjectUrlException.class)
                .hasFieldOrPropertyWithValue("rejectedReason", "unsafe_url");
        assertThatThrownBy(
                        () -> guard.pin("http://files.aura.test:9000/aura-vton/person/x.jpg"))
                .isInstanceOf(UnsafeObjectUrlException.class);
    }

    @Test
    void workerOutputsAllowedOnlyOnResultPin() throws Exception {
        StorageUrlGuard guard = new StorageUrlGuard(
                localMinio(),
                host -> {
                    try {
                        return InetAddress.getAllByName(host);
                    } catch (java.net.UnknownHostException exception) {
                        throw new IllegalStateException(exception);
                    }
                },
                java.time.Clock.systemUTC(),
                Duration.ofMinutes(5),
                Duration.ofMinutes(5),
                2,
                "http://127.0.0.1:8001");
        assertThatThrownBy(() -> guard.pin("http://127.0.0.1:8001/outputs/1.png"))
                .isInstanceOf(UnsafeObjectUrlException.class);
        guard.pinResult("http://127.0.0.1:8001/outputs/1.png");
        assertThatThrownBy(() -> guard.pinResult("http://127.0.0.1:8001/internal"))
                .isInstanceOf(UnsafeObjectUrlException.class);
    }

    @Test
    void dnsRebindingToMetadataIsRejected() throws Exception {
        InetAddress cdn = InetAddress.getByName("198.51.100.10");
        InetAddress metadata = InetAddress.getByName("169.254.169.254");
        AtomicInteger resolves = new AtomicInteger();
        StorageProperties cdnStorage = cdnStorage();
        StorageUrlGuard rebound = new StorageUrlGuard(cdnStorage, host -> {
            if (!"files.aura.test".equals(host)) {
                throw new IllegalStateException(host);
            }
            // 1: origin pin (cdn). 2: istek cozumu (metadata). 3: mismatch refresh (cdn).
            if (resolves.incrementAndGet() == 2) {
                return new InetAddress[] {metadata};
            }
            return new InetAddress[] {cdn};
        });
        assertThatThrownBy(
                        () -> rebound.rejectUnsafeObjectUrl(
                                "http://files.aura.test:9000/aura-vton/person/x.jpg"))
                .isInstanceOf(UnsafeObjectUrlException.class)
                .hasFieldOrPropertyWithValue("rejectedReason", "unsafe_url");
    }

    @Test
    void blankUrlIsSkipped() {
        guard.rejectUnsafeObjectUrl(null);
        guard.rejectUnsafeObjectUrl("  ");
    }

    private static void assertBlocked(String url) {
        StorageUrlGuard local = new StorageUrlGuard(localMinio());
        assertThatThrownBy(() -> local.rejectUnsafeObjectUrl(url))
                .isInstanceOf(UnsafeObjectUrlException.class)
                .hasFieldOrPropertyWithValue("rejectedReason", "unsafe_url");
    }

    private static StorageProperties cdnStorage() {
        return new StorageProperties(
                "s3",
                "http://files.aura.test:9000",
                "http://files.aura.test:9000",
                "us-east-1",
                "test",
                "test",
                true,
                900,
                "aura-wardrobe",
                "aura-vton",
                "aura-avatars");
    }

    private static StorageProperties localMinio() {
        return new StorageProperties(
                "memory",
                "http://127.0.0.1:9000",
                "http://127.0.0.1:9000",
                "us-east-1",
                "test",
                "test",
                true,
                900,
                "aura-wardrobe",
                "aura-vton",
                "aura-avatars");
    }
}
