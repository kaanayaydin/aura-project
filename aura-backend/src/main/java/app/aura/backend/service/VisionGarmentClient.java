package app.aura.backend.service;

import app.aura.backend.config.VisionProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * aura-vision garment studio normalize istemcisi.
 */
@Service
public class VisionGarmentClient {

    private static final Logger log = LoggerFactory.getLogger(VisionGarmentClient.class);

    private final VisionProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public VisionGarmentClient(VisionProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeoutSeconds() * 1000);
        factory.setReadTimeout(properties.readTimeoutSeconds() * 1000);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /** Test / manuel enjekte. */
    VisionGarmentClient(VisionProperties properties, RestClient restClient, ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public boolean isEnabled() {
        return properties.normalizeGarmentEnabled();
    }

    /**
     * Ham/cutout baytlari stüdyo PNG'ye cevirir. Basarisizsa empty.
     *
     * {@code skipOrientation=true}: Vision deskew/cardinal/ensemble atlar,
     * yalniz 3:4 framing uygular (onayli rotasyonu ezmez).
     */
    public Optional<byte[]> normalizeGarmentPng(byte[] imageBytes, String filename) {
        return normalizeGarmentPng(imageBytes, filename, false);
    }

    public Optional<byte[]> normalizeGarmentPng(
            byte[] imageBytes, String filename, boolean skipOrientation) {
        if (!properties.normalizeGarmentEnabled()) {
            return Optional.empty();
        }
        if (imageBytes == null || imageBytes.length == 0) {
            return Optional.empty();
        }
        try {
            MultipartBodyBuilder body = new MultipartBodyBuilder();
            body.part("file", imageBytes)
                    .filename(filename == null || filename.isBlank() ? "garment.png" : filename)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM);
            body.part("skip_orientation", skipOrientation ? "true" : "false");

            String response = restClient.post()
                    .uri(properties.normalizeGarmentUrl())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body.build())
                    .retrieve()
                    .body(String.class);

            if (response == null || response.isBlank()) {
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(response);
            String b64 = root.path("image_base64").asText(null);
            if (b64 == null || b64.isBlank()) {
                b64 = root.path("imageBase64").asText(null);
            }
            if (b64 == null || b64.isBlank()) {
                log.warn("Vision normalize cevabinda image_base64 yok");
                return Optional.empty();
            }
            byte[] png = Base64.getDecoder().decode(b64);
            if (!isPng(png)) {
                log.warn(
                        "Vision normalize gecersiz PNG (bytes={} magic={})",
                        png.length,
                        png.length >= 4
                                ? String.format(
                                        "%02x%02x%02x%02x",
                                        png[0] & 0xff, png[1] & 0xff, png[2] & 0xff, png[3] & 0xff)
                                : "short");
                return Optional.empty();
            }
            log.info(
                    "Vision normalize OK source={} bytes={} contentType=image/png",
                    root.path("cutout_source").asText("?"),
                    png.length);
            return Optional.of(png);
        } catch (RestClientException | IllegalArgumentException | java.io.IOException ex) {
            log.warn("Vision normalize basarisiz, ham gorsel kullanilacak: {}", ex.toString());
            return Optional.empty();
        }
    }

    private static boolean isPng(byte[] bytes) {
        return bytes != null
                && bytes.length >= 8
                && (bytes[0] & 0xff) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4e
                && bytes[3] == 0x47
                && bytes[4] == 0x0d
                && bytes[5] == 0x0a
                && bytes[6] == 0x1a
                && bytes[7] == 0x0a;
    }
}
