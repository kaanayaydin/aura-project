package app.aura.backend.support;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

/** keytool ile gercek self-signed sertifika (mock TLS yok). */
final class SelfSignedTls {

    record Material(SSLContext serverContext, SSLSocketFactory clientFactory, X509Certificate cert) {}

    static Material generate(String commonName) throws Exception {
        Path dir = Files.createTempDirectory("aura-tls-");
        Path p12 = dir.resolve("server.p12");
        String pass = "testpass";
        Path keytool = Path.of(System.getProperty("java.home"), "bin", "keytool");
        Process process = new ProcessBuilder(
                        keytool.toString(),
                        "-genkeypair",
                        "-alias",
                        "server",
                        "-dname",
                        "CN=" + commonName,
                        "-ext",
                        "SAN=dns:" + commonName,
                        "-keyalg",
                        "RSA",
                        "-keysize",
                        "2048",
                        "-validity",
                        "2",
                        "-keystore",
                        p12.toAbsolutePath().toString(),
                        "-storetype",
                        "PKCS12",
                        "-storepass",
                        pass,
                        "-keypass",
                        pass,
                        "-noprompt")
                .redirectErrorStream(true)
                .start();
        String log = new String(process.getInputStream().readAllBytes());
        int code = process.waitFor();
        if (code != 0) {
            throw new IllegalStateException("keytool failed: " + log);
        }
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(p12)) {
            ks.load(in, pass.toCharArray());
        }
        X509Certificate cert = (X509Certificate) ks.getCertificate("server");
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, pass.toCharArray());
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);
        SSLContext server = SSLContext.getInstance("TLS");
        server.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        SSLContext client = SSLContext.getInstance("TLS");
        client.init(null, tmf.getTrustManagers(), null);
        Files.deleteIfExists(p12);
        return new Material(server, client.getSocketFactory(), cert);
    }

    static void writeHttpPng(OutputStream out, byte[] png) throws Exception {
        String headers =
                "HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nContent-Length: "
                        + png.length
                        + "\r\nConnection: close\r\n\r\n";
        out.write(headers.getBytes());
        out.write(png);
        out.flush();
    }

    private SelfSignedTls() {}
}
