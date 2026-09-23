package app.aura.backend.support;

import app.aura.backend.support.StorageUrlGuard.PinnedTarget;
import app.aura.backend.web.InvalidImagePayloadException;
import app.aura.backend.web.UnsafeObjectUrlException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Object URL indirme — StorageUrlGuard pin + dogrulanmis IP'ye TCP.
 *
 * HttpURLConnection Host'u kisitlar ve hostname ile yeniden DNS yapabilir.
 * Ham soket: TCP pin'li IP, HTTP Host + TLS SNI orijinal hostname.
 * Wardrobe ve ileride ucuncu yol bu component'i kullanmali.
 */
@Component
public class PinnedHttpDownloader {

    private static final int MAX_REDIRECTS = 2;
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 15_000;
    private static final int CHUNK = 8 * 1024;

    private static final Logger log = LoggerFactory.getLogger(PinnedHttpDownloader.class);

    private final StorageUrlGuard storageUrlGuard;

    public PinnedHttpDownloader(StorageUrlGuard storageUrlGuard) {
        this.storageUrlGuard = storageUrlGuard;
    }

    public byte[] download(String rawUrl, int maxBytes) {
        if (maxBytes <= 0) {
            maxBytes = StorageUrlGuard.MAX_DOWNLOAD_BYTES;
        }
        String current = rawUrl;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            PinnedTarget target = storageUrlGuard.pin(current);
            HopResponse hopResponse;
            try {
                hopResponse = exchange(target, maxBytes);
            } catch (UnsafeObjectUrlException | InvalidImagePayloadException exception) {
                throw exception;
            } catch (IOException exception) {
                log.warn("Pinned HTTP indirme basarisiz url={}: {}", current, exception.toString());
                throw new InvalidImagePayloadException("Gorsel URL indirilemedi.");
            }
            int status = hopResponse.status();
            if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                String location = hopResponse.header("location");
                if (location == null || location.isBlank()) {
                    throw new UnsafeObjectUrlException("unsafe_url", "Gorsel yonlendirme Location yok");
                }
                current = URI.create(current).resolve(location).toString();
                continue;
            }
            if (status < 200 || status >= 300) {
                throw new InvalidImagePayloadException("Gorsel URL HTTP " + status + " dondurdu");
            }
            return hopResponse.body();
        }
        throw new UnsafeObjectUrlException("unsafe_url", "Gorsel URL cok fazla yonlendirme");
    }

    private HopResponse exchange(PinnedTarget target, int maxBytes) throws IOException {
        log.debug(
                "Pinned HTTP TCP ip={} sni/host={} port={}",
                target.connectIp().getHostAddress(),
                target.hostname(),
                target.port());
        try (Socket socket = openSocket(target)) {
            String path = target.path() == null || target.path().isBlank() ? "/" : target.path();
            if (target.query() != null && !target.query().isBlank()) {
                path = path + "?" + target.query();
            }
            String request =
                    "GET " + path + " HTTP/1.1\r\n"
                            + "Host: " + target.hostHeader() + "\r\n"
                            + "Connection: close\r\n\r\n";
            OutputStream out = socket.getOutputStream();
            out.write(request.getBytes(StandardCharsets.US_ASCII));
            out.flush();
            return readResponse(socket.getInputStream(), maxBytes);
        }
    }

    private static Socket openSocket(PinnedTarget target) throws IOException {
        Socket tcp = new Socket();
        tcp.connect(new InetSocketAddress(target.connectIp(), target.port()), CONNECT_TIMEOUT_MS);
        tcp.setSoTimeout(READ_TIMEOUT_MS);
        if (!"https".equals(target.scheme())) {
            return tcp;
        }
        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        SSLSocket ssl = (SSLSocket) factory.createSocket(tcp, target.hostname(), target.port(), true);
        SSLParameters params = ssl.getSSLParameters();
        params.setServerNames(List.of(new SNIHostName(target.hostname())));
        ssl.setSSLParameters(params);
        ssl.startHandshake();
        return ssl;
    }

    private static HopResponse readResponse(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream headerBuf = new ByteArrayOutputStream();
        int cur;
        while ((cur = in.read()) >= 0) {
            headerBuf.write(cur);
            byte[] soFar = headerBuf.toByteArray();
            int n = soFar.length;
            if (n >= 4
                    && soFar[n - 4] == '\r'
                    && soFar[n - 3] == '\n'
                    && soFar[n - 2] == '\r'
                    && soFar[n - 1] == '\n') {
                break;
            }
            if (n >= 2 && soFar[n - 2] == '\n' && soFar[n - 1] == '\n') {
                break;
            }
            if (n > 64 * 1024) {
                throw new InvalidImagePayloadException("Gorsel URL HTTP basligi cok buyuk");
            }
        }
        String rawHeaders = headerBuf.toString(StandardCharsets.US_ASCII);
        String[] lines = rawHeaders.split("\r?\n");
        if (lines.length == 0 || !lines[0].startsWith("HTTP/")) {
            throw new InvalidImagePayloadException("Gorsel URL HTTP yaniti gecersiz");
        }
        int status = parseStatus(lines[0]);
        Map<String, String> headers = new LinkedHashMap<>();
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon <= 0) {
                continue;
            }
            headers.put(
                    lines[i].substring(0, colon).trim().toLowerCase(Locale.ROOT),
                    lines[i].substring(colon + 1).trim());
        }
        if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
            return new HopResponse(status, headers, new byte[0]);
        }
        String cl = headers.get("content-length");
        if (cl != null) {
            long declared;
            try {
                declared = Long.parseLong(cl);
            } catch (NumberFormatException exception) {
                throw new InvalidImagePayloadException("Gorsel URL Content-Length gecersiz");
            }
            if (declared > maxBytes) {
                try {
                    in.close();
                } catch (IOException ignored) {
                    // baglanti kesildi
                }
                throw new InvalidImagePayloadException(
                        "Gorsel %d byte sinirini asiyor.".formatted(maxBytes));
            }
        }
        byte[] body = readCappedBody(in, maxBytes);
        return new HopResponse(status, headers, body);
    }

    private static byte[] readCappedBody(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[CHUNK];
        int total = 0;
        int n;
        while ((n = in.read(buf)) >= 0) {
            if (n == 0) {
                continue;
            }
            total += n;
            if (total > maxBytes) {
                throw new InvalidImagePayloadException(
                        "Gorsel %d byte sinirini asiyor.".formatted(maxBytes));
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private static int parseStatus(String statusLine) {
        String[] parts = statusLine.trim().split(" ");
        if (parts.length < 2) {
            throw new InvalidImagePayloadException("Gorsel URL HTTP yaniti gecersiz");
        }
        try {
            return Integer.parseInt(parts[1]);
        } catch (NumberFormatException exception) {
            throw new InvalidImagePayloadException("Gorsel URL HTTP yaniti gecersiz");
        }
    }

    private record HopResponse(int status, Map<String, String> headers, byte[] body) {
        String header(String name) {
            return headers.get(name.toLowerCase(Locale.ROOT));
        }
    }
}
