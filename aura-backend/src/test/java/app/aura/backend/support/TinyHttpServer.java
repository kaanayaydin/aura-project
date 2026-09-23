package app.aura.backend.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/** Test HTTP sunucusu — Host header ve istek sayaci. */
public final class TinyHttpServer implements AutoCloseable {

    private final ServerSocket serverSocket;
    private final Thread thread;
    private final AtomicInteger hits = new AtomicInteger();
    private volatile String lastHostHeader = "";
    private volatile byte[] body;
    private volatile String extraHeaders = "";
    private volatile int status = 200;
    private volatile boolean omitContentLength;
    private volatile boolean slowBody;

    public TinyHttpServer(byte[] body) throws IOException {
        this.body = body == null ? new byte[0] : body;
        this.serverSocket = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
        this.thread = new Thread(this::serve, "tiny-http");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    public int port() {
        return serverSocket.getLocalPort();
    }

    public int hits() {
        return hits.get();
    }

    public String lastHostHeader() {
        return lastHostHeader;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public void setExtraHeaders(String extraHeaders) {
        this.extraHeaders = extraHeaders == null ? "" : extraHeaders;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }

    public void setSlowBody(boolean slowBody) {
        this.slowBody = slowBody;
    }

    public void omitContentLength() {
        this.omitContentLength = true;
    }

    private void serve() {
        while (!serverSocket.isClosed()) {
            try (Socket socket = serverSocket.accept()) {
                hits.incrementAndGet();
                lastHostHeader = readHost(socket.getInputStream());
                OutputStream out = socket.getOutputStream();
                byte[] payload = body;
                boolean hasCl = omitContentLength
                        || extraHeaders.toLowerCase().contains("content-length:");
                String headers =
                        "HTTP/1.1 " + status + " OK\r\n"
                                + "Content-Type: image/png\r\n"
                                + extraHeaders
                                + (hasCl ? "" : "Content-Length: " + payload.length + "\r\n")
                                + "Connection: close\r\n\r\n";
                out.write(headers.getBytes(StandardCharsets.US_ASCII));
                if (slowBody) {
                    for (byte b : payload) {
                        out.write(b);
                        out.flush();
                    }
                } else {
                    out.write(payload);
                }
                out.flush();
            } catch (IOException ignored) {
                if (serverSocket.isClosed()) {
                    return;
                }
            }
        }
    }

    private static String readHost(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int cur;
        while ((cur = in.read()) >= 0) {
            buf.write(cur);
            String soFar = buf.toString(StandardCharsets.US_ASCII);
            if (soFar.contains("\r\n\r\n") || soFar.contains("\n\n")) {
                break;
            }
            if (buf.size() > 16_384) {
                break;
            }
        }
        String raw = buf.toString(StandardCharsets.US_ASCII);
        for (String line : raw.split("\r?\n")) {
            if (line.toLowerCase().startsWith("host:")) {
                return line.substring(5).trim();
            }
        }
        return "";
    }

    @Override
    public void close() {
        try {
            serverSocket.close();
        } catch (IOException ignored) {
            // test teardown
        }
        thread.interrupt();
    }
}
