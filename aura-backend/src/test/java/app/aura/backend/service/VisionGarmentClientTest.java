package app.aura.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import app.aura.backend.config.VisionProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class VisionGarmentClientTest {

    private MockRestServiceServer server;
    private VisionGarmentClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://vision.test");
        server = MockRestServiceServer.bindTo(builder).build();
        VisionProperties props = new VisionProperties("http://vision.test", true, 2, 5);
        client = new VisionGarmentClient(props, builder.build(), new ObjectMapper());
    }

    @Test
    void normalizeGarmentPng_decodesBase64FromVision() {
        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        String b64 = Base64.getEncoder().encodeToString(png);
        String json = """
                {"status":"success","image_base64":"%s","cutout_source":"alpha","width":100,"height":100}
                """.formatted(b64);

        server.expect(requestTo("http://vision.test/api/v1/vision/normalize-garment"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<byte[]> out = client.normalizeGarmentPng(new byte[] {1, 2, 3}, "tee.png");
        assertThat(out).isPresent();
        assertThat(out.get()).startsWith(new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47});
        server.verify();
    }

    @Test
    void skipOrientationTrue_sendsSkipOrientationFormField() {
        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        String b64 = Base64.getEncoder().encodeToString(png);
        String json = """
                {"status":"success","image_base64":"%s","cutout_source":"alpha","width":768,"height":1024}
                """.formatted(b64);

        server.expect(requestTo("http://vision.test/api/v1/vision/normalize-garment"))
                .andExpect(request -> {
                    MockClientHttpRequest mock = (MockClientHttpRequest) request;
                    String payload = new String(mock.getBodyAsBytes(), StandardCharsets.ISO_8859_1);
                    assertThat(payload).contains("skip_orientation");
                    assertThat(payload).contains("true");
                })
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<byte[]> out = client.normalizeGarmentPng(new byte[] {1, 2, 3}, "tee.png", true);
        assertThat(out).isPresent();
        server.verify();
    }

    @Test
    void disabledClientReturnsEmpty() {
        VisionProperties props = new VisionProperties("http://vision.test", false, 1, 1);
        VisionGarmentClient disabled = new VisionGarmentClient(props, RestClient.create(), new ObjectMapper());
        assertThat(disabled.normalizeGarmentPng(new byte[] {1}, "x.png")).isEmpty();
    }
}
