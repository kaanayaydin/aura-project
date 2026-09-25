package app.aura.backend.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.aura.backend.config.StorageProperties;
import app.aura.backend.web.InvalidImagePayloadException;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;

class PinnedHttpDownloaderTlsTest {

    private static final byte[] PNG = {
        (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01, 0x02, 0x03
    };

    @Test
    void evilCertificateForDifferentCnIsRejected() throws Exception {
        SelfSignedTls.Material evil = SelfSignedTls.generate("evil.example.com");
        HttpsServer server = httpsServer(evil, PNG);
        try {
            int port = server.getAddress().getPort();
            InetAddress loop = InetAddress.getByName("127.0.0.1");
            StorageUrlGuard guard = new StorageUrlGuard(
                    httpsOrigin("files.aura.test", port), host -> new InetAddress[] {loop});
            PinnedHttpDownloader downloader = new PinnedHttpDownloader(guard, evil.clientFactory());
            assertThatThrownBy(() -> downloader.download(
                            "https://files.aura.test:" + port + "/aura-vton/person/x.jpg",
                            1_000_000))
                    .isInstanceOf(InvalidImagePayloadException.class)
                    .hasMessageContaining("TLS hostname");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void matchingCertificateIsAccepted() throws Exception {
        SelfSignedTls.Material files = SelfSignedTls.generate("files.aura.test");
        HttpsServer server = httpsServer(files, PNG);
        try {
            int port = server.getAddress().getPort();
            InetAddress loop = InetAddress.getByName("127.0.0.1");
            StorageUrlGuard guard = new StorageUrlGuard(
                    httpsOrigin("files.aura.test", port), host -> new InetAddress[] {loop});
            PinnedHttpDownloader downloader = new PinnedHttpDownloader(guard, files.clientFactory());
            byte[] body = downloader.download(
                    "https://files.aura.test:" + port + "/aura-vton/person/x.jpg", 1_000_000);
            assertThat(body).isEqualTo(PNG);
        } finally {
            server.stop(0);
        }
    }

    private static HttpsServer httpsServer(SelfSignedTls.Material material, byte[] body) throws Exception {
        HttpsServer server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(material.serverContext()));
        server.createContext("/aura-vton/person/x.jpg", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
    }

    private static StorageProperties httpsOrigin(String host, int port) {
        String base = "https://" + host + ":" + port;
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
}
