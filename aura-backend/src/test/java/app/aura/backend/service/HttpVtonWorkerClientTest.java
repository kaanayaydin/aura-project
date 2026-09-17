package app.aura.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class HttpVtonWorkerClientTest {

    private MockRestServiceServer server;
    private HttpVtonWorkerClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://vton.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HttpVtonWorkerClient(builder.build());
    }

    @Test
    void enqueuePostsGarmentAndPersonImages() {
        server.expect(requestTo("http://vton.test/internal/vton/enqueue"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.jobId").value(42))
                .andExpect(jsonPath("$.wardrobeItemId").value(7))
                .andExpect(jsonPath("$.personImageBase64").value("person-b64"))
                .andExpect(jsonPath("$.garmentImageBase64").value("garment-b64"))
                .andExpect(jsonPath("$.personImageUrl").value("http://person"))
                .andExpect(jsonPath("$.garmentImageUrl").value("http://garment"))
                .andExpect(jsonPath("$.clothType").value("lower"))
                .andRespond(withSuccess(
                        """
                        {"jobId":"42","workerJobId":"42","celeryTaskId":"abc","status":"QUEUED"}
                        """,
                        MediaType.APPLICATION_JSON));

        String workerJobId = client.enqueue(new VtonWorkerClient.EnqueuePayload(
                42L, 1L, 7L, "person-b64", "garment-b64", "http://person", "http://garment", "lower"));
        assertThat(workerJobId).isEqualTo("42");
        server.verify();
    }

    @Test
    void statusMapsCompletedSnapshotWithBase64() {
        server.expect(requestTo("http://vton.test/internal/vton/status/42"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        """
                        {
                          "jobId":"42",
                          "status":"COMPLETED",
                          "resultImageUri":"mock://catvton/result/42",
                          "resultImageBase64":"iVBOR"
                        }
                        """,
                        MediaType.APPLICATION_JSON));

        var snapshot = client.status("42");
        assertThat(snapshot.status()).isEqualTo("COMPLETED");
        assertThat(snapshot.resultImageUri()).contains("catvton");
        assertThat(snapshot.resultImageBase64()).isEqualTo("iVBOR");
        server.verify();
    }
}
