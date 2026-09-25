package app.aura.backend.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.aura.backend.config.StorageProperties;
import app.aura.backend.web.InvalidImagePayloadException;
import app.aura.backend.web.UnsafeObjectUrlException;
import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PinnedHttpDownloaderTest {

    private static final byte[] PNG = {
        (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01
    };

    @Test
    void connectUsesPinnedIpNotLaterDnsAndPreservesHostHeader() throws Exception {
        try (TinyHttpServer server = new TinyHttpServer(PNG)) {
            InetAddress loop = InetAddress.getByName("127.0.0.1");
            InetAddress metadata = InetAddress.getByName("169.254.169.254");
            AtomicInteger resolves = new AtomicInteger();
            StorageUrlGuard guard = new StorageUrlGuard(
                    originAt("files.aura.test", server.port()),
                    host -> {
                        if (!"files.aura.test".equals(host)) {
                            throw new IllegalStateException(host);
                        }
                        if (resolves.incrementAndGet() <= 3) {
                            return new InetAddress[] {loop};
                        }
                        return new InetAddress[] {metadata};
                    });
            PinnedHttpDownloader downloader = new PinnedHttpDownloader(guard);
            byte[] body = downloader.download(
                    "http://files.aura.test:" + server.port() + "/aura-wardrobe/person/x.png",
                    1_000_000);
            assertThat(body).isEqualTo(PNG);
            assertThat(server.hits()).isEqualTo(1);
            assertThat(server.lastHostHeader()).isEqualTo("files.aura.test:" + server.port());
            // 1 origin refresh (unique host) + 1 pin resolve + mismatch yok.
            // 3. cagri hâlâ iyi IP; 4+ metadata olurdu — hostname ile baglansak UnknownHost
            // veya 4. resolve. n<=3 kalmali.
            assertThat(resolves.get()).isLessThanOrEqualTo(3);
        }
    }

    @Test
    void contentLengthOverCapRejectedWithoutReadingBody() throws Exception {
        try (TinyHttpServer server = new TinyHttpServer(PNG)) {
            server.setExtraHeaders("Content-Length: 838860800\r\n");
            StorageUrlGuard guard = new StorageUrlGuard(localMinio(server.port()));
            PinnedHttpDownloader downloader = new PinnedHttpDownloader(guard);
            assertThatThrownBy(() -> downloader.download(
                            "http://127.0.0.1:" + server.port() + "/aura-wardrobe/huge.png",
                            1024))
                    .isInstanceOf(InvalidImagePayloadException.class);
        }
    }

    @Test
    void streamCutsWhenBodyExceedsCap() throws Exception {
        byte[] fat = new byte[8192];
        try (TinyHttpServer server = new TinyHttpServer(fat)) {
            server.setSlowBody(true);
            server.omitContentLength();
            StorageUrlGuard guard = new StorageUrlGuard(localMinio(server.port()));
            PinnedHttpDownloader downloader = new PinnedHttpDownloader(guard);
            assertThatThrownBy(() -> downloader.download(
                            "http://127.0.0.1:" + server.port() + "/aura-wardrobe/stream.png",
                            1024))
                    .isInstanceOf(InvalidImagePayloadException.class);
        }
    }

    @Test
    void redirectToMetadataNeverConnects() throws Exception {
        try (TinyHttpServer server = new TinyHttpServer(PNG)) {
            server.setStatus(302);
            server.setExtraHeaders("Location: http://169.254.169.254/latest/meta-data/\r\n");
            StorageUrlGuard guard = new StorageUrlGuard(localMinio(server.port()));
            PinnedHttpDownloader downloader = new PinnedHttpDownloader(guard);
            assertThatThrownBy(() -> downloader.download(
                            "http://127.0.0.1:" + server.port() + "/aura-wardrobe/x.png",
                            1_000_000))
                    .isInstanceOf(UnsafeObjectUrlException.class);
            assertThat(server.hits()).isEqualTo(1);
        }
    }

    @Test
    void pinTtlRefreshPicksUpRotatedCdnIp() throws Exception {
        InetAddress first = InetAddress.getByName("198.51.100.10");
        InetAddress rotated = InetAddress.getByName("198.51.100.20");
        AtomicInteger resolves = new AtomicInteger();
        MutableClock clock = new MutableClock(Instant.parse("2026-09-22T00:00:00Z"));
        StorageUrlGuard guard = new StorageUrlGuard(
                originAt("files.aura.test", 9000),
                host -> {
                    if (resolves.get() == 0) {
                        resolves.incrementAndGet();
                        return new InetAddress[] {first};
                    }
                    resolves.incrementAndGet();
                    return new InetAddress[] {rotated};
                },
                clock,
                Duration.ofMinutes(5));
        clock.plus(Duration.ofMinutes(6));
        assertThatThrownBy(
                        () -> guard.pin("http://files.aura.test:9000/aura-vton/person/x.jpg"))
                .isInstanceOf(UnsafeObjectUrlException.class);
        assertThatThrownBy(
                        () -> guard.pin("http://files.aura.test:9000/aura-vton/person/x.jpg"))
                .isInstanceOf(UnsafeObjectUrlException.class);
        clock.plus(Duration.ofMinutes(6));
        StorageUrlGuard.PinnedTarget target = guard.pin(
                "http://files.aura.test:9000/aura-vton/person/x.jpg");
        assertThat(target.connectIp().getHostAddress()).isEqualTo("198.51.100.20");
    }

    @Test
    void downloadResultAllowsWorkerOutputsNotInternal() throws Exception {
        try (TinyHttpServer server = new TinyHttpServer(PNG)) {
            String worker = "http://127.0.0.1:" + server.port();
            StorageUrlGuard guard = new StorageUrlGuard(
                    localMinio(9000),
                    host -> {
                        try {
                            return new InetAddress[] {InetAddress.getByName("127.0.0.1")};
                        } catch (java.net.UnknownHostException exception) {
                            throw new IllegalStateException(exception);
                        }
                    },
                    Clock.systemUTC(),
                    Duration.ofMinutes(5),
                    Duration.ofMinutes(5),
                    2,
                    worker);
            PinnedHttpDownloader downloader = new PinnedHttpDownloader(guard);
            assertThatThrownBy(() -> downloader.download(worker + "/outputs/1.png", 1_000_000))
                    .isInstanceOf(UnsafeObjectUrlException.class);
            byte[] body = downloader.downloadResult(worker + "/outputs/1.png", 1_000_000);
            assertThat(body).isEqualTo(PNG);
            assertThatThrownBy(() -> downloader.downloadResult(worker + "/internal", 1_000_000))
                    .isInstanceOf(UnsafeObjectUrlException.class);
        }
    }

    private static StorageProperties originAt(String host, int port) {
        String base = "http://" + host + ":" + port;
        return new StorageProperties(
                "s3",
                base,
                base,
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

    private static StorageProperties localMinio(int port) {
        return originAt("127.0.0.1", port);
    }

    static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void plus(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
