package app.aura.backend.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.aura.backend.config.StorageProperties;
import app.aura.backend.web.UnsafeObjectUrlException;
import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assumptions;
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
    void configuredEndpointHostIsPinnedWithRealDns() throws Exception {
        String endpoint = System.getenv("AURA_S3_ENDPOINT");
        Assumptions.assumeTrue(endpoint != null && endpoint.contains("r2.cloudflarestorage.com"));
        URI origin = URI.create(endpoint.trim());
        String host = origin.getHost();
        InetAddress[] live = InetAddress.getAllByName(host);
        assertThat(live).isNotEmpty();
        assertThat(Arrays.stream(live).noneMatch(InetAddress::isLoopbackAddress)).isTrue();

        StorageUrlGuard r2 = new StorageUrlGuard(storage(endpoint, endpoint));
        String url = endpoint.replaceAll("/$", "") + "/aura-wardrobe/items/dns-probe.png";
        StorageUrlGuard.PinnedTarget pinned = r2.pin(url);
        assertThat(pinned.connectIps()).containsExactlyInAnyOrder(live);
    }

    @Test
    void r2PublicHostUrlDropsBucketFromPathAndIsAllowlisted() throws Exception {
        StorageProperties r2 = new StorageProperties(
                "s3",
                "https://example.r2.cloudflarestorage.com",
                "https://example.r2.cloudflarestorage.com",
                "auto",
                "test",
                "test",
                true,
                900,
                "aura-wardrobe",
                "aura-vton",
                "aura-avatars",
                "https://pub-wardrobe.example.r2.dev",
                "https://pub-vton.example.r2.dev",
                "https://pub-avatars.example.r2.dev");
        assertThat(r2.publicObjectUrl("aura-wardrobe", "items/abc.png"))
                .isEqualTo("https://pub-wardrobe.example.r2.dev/items/abc.png");
        InetAddress pinned = InetAddress.getByAddress(new byte[] {127, 0, 0, 2});
        StorageUrlGuard r2Guard = new StorageUrlGuard(r2, host -> new InetAddress[] {pinned});
        r2Guard.rejectUnsafeObjectUrl("https://pub-wardrobe.example.r2.dev/items/abc.png");
        assertThatThrownBy(() -> r2Guard.rejectUnsafeObjectUrl("https://pub-other.example.r2.dev/items/abc.png"))
                .isInstanceOf(UnsafeObjectUrlException.class);
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
                "aura-avatars",
                null,
                null,
                null);
    }

    private static StorageProperties storage(String endpoint, String publicBase) {
        return new StorageProperties(
                "s3",
                endpoint,
                publicBase,
                "auto",
                "test",
                "test",
                true,
                900,
                "aura-wardrobe",
                "aura-vton",
                "aura-avatars",
                null,
                null,
                null);
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
                "aura-avatars",
                null,
                null,
                null);
    }
}
