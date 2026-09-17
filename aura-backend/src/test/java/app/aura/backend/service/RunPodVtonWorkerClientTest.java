package app.aura.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import app.aura.backend.config.VtonProperties;
import app.aura.backend.web.VtonWorkerUnavailableException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RunPodVtonWorkerClientTest {

    private MockRestServiceServer server;
    private RestClient.Builder builder;
    private AtomicInteger sleepCalls;
    private RunPodVtonWorkerClient client;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl("https://api.runpod.ai/v2/endpoint-xyz");
        server = MockRestServiceServer.bindTo(builder).build();
        sleepCalls = new AtomicInteger();
        client = newClient(true, 3L);
    }

    @Test
    void enqueueWrapsPayloadInInputAndSendsBearer() {
        server.expect(requestTo("https://api.runpod.ai/v2/endpoint-xyz/run"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer rp-secret-key"))
                .andExpect(jsonPath("$.input.jobId").value(42))
                .andExpect(jsonPath("$.input.userId").value(1))
                .andExpect(jsonPath("$.input.wardrobeItemId").value(7))
                .andExpect(jsonPath("$.input.personImageBase64").value("person-b64"))
                .andExpect(jsonPath("$.input.garmentImageBase64").value("garment-b64"))
                .andExpect(jsonPath("$.input.personImageUrl").value("http://person"))
                .andExpect(jsonPath("$.input.garmentImageUrl").value("http://garment"))
                .andExpect(jsonPath("$.input.clothType").value("lower"))
                .andRespond(withSuccess(
                        """
                        {"id":"rp-job-99","status":"IN_QUEUE"}
                        """,
                        MediaType.APPLICATION_JSON));

        String workerJobId = client.enqueue(new VtonWorkerClient.EnqueuePayload(
                42L, 1L, 7L, "person-b64", "garment-b64", "http://person", "http://garment", "lower"));

        assertThat(workerJobId).isEqualTo("rp-job-99");
        server.verify();
    }

    @Test
    void statusMapsRunPodCompletedOutput() {
        server.expect(requestTo("https://api.runpod.ai/v2/endpoint-xyz/status/rp-job-99"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer rp-secret-key"))
                .andRespond(withSuccess(
                        """
                        {
                          "id":"rp-job-99",
                          "status":"COMPLETED",
                          "output":{
                            "ok":true,
                            "status":"COMPLETED",
                            "resultImageUri":"mock://catvton/result/42",
                            "resultImageBase64":"iVBOR"
                          }
                        }
                        """,
                        MediaType.APPLICATION_JSON));

        var snapshot = client.status("rp-job-99");
        assertThat(snapshot.status()).isEqualTo("COMPLETED");
        assertThat(snapshot.resultImageUri()).contains("catvton");
        assertThat(snapshot.resultImageBase64()).isEqualTo("iVBOR");
        server.verify();
    }

    @Test
    void statusMapsInQueueAndInProgress() {
        assertThat(RunPodVtonWorkerClient.mapRunPodStatus("IN_QUEUE")).isEqualTo("QUEUED");
        assertThat(RunPodVtonWorkerClient.mapRunPodStatus("IN_PROGRESS")).isEqualTo("PROCESSING");
        assertThat(RunPodVtonWorkerClient.mapRunPodStatus("FAILED")).isEqualTo("FAILED");
    }

    @Test
    void enqueueRetriesOnceAfterTimeoutThenSucceeds() {
        server.expect(requestTo("https://api.runpod.ai/v2/endpoint-xyz/run"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        server.expect(requestTo("https://api.runpod.ai/v2/endpoint-xyz/run"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        """
                        {"id":"rp-after-retry","status":"IN_QUEUE"}
                        """,
                        MediaType.APPLICATION_JSON));

        String workerJobId = client.enqueue(samplePayload());

        assertThat(workerJobId).isEqualTo("rp-after-retry");
        assertThat(sleepCalls.get()).isEqualTo(1);
        server.verify();
    }

    @Test
    void enqueueThrowsProblemDetailFriendlyErrorAfterRetryExhausted() {
        server.expect(requestTo("https://api.runpod.ai/v2/endpoint-xyz/run"))
                .andRespond(withException(new IOException("connect timed out")));
        server.expect(requestTo("https://api.runpod.ai/v2/endpoint-xyz/run"))
                .andRespond(withException(new IOException("connect timed out")));

        assertThatThrownBy(() -> client.enqueue(samplePayload()))
                .isInstanceOf(VtonWorkerUnavailableException.class)
                .hasMessageContaining("erisilemiyor");
        assertThat(sleepCalls.get()).isEqualTo(1);
        server.verify();
    }

    @Test
    void enqueueDoesNotRetryWhenDisabled() {
        client = newClient(false, 3L);

        server.expect(requestTo("https://api.runpod.ai/v2/endpoint-xyz/run"))
                .andRespond(withException(new IOException("connect timed out")));

        assertThatThrownBy(() -> client.enqueue(samplePayload()))
                .isInstanceOf(VtonWorkerUnavailableException.class);
        assertThat(sleepCalls.get()).isZero();
        server.verify();
    }

    private static VtonWorkerClient.EnqueuePayload samplePayload() {
        return new VtonWorkerClient.EnqueuePayload(1L, 1L, 1L, "p", "g", null, null, "upper");
    }

    private RunPodVtonWorkerClient newClient(boolean retryEnabled, long delayMs) {
        VtonProperties properties = new VtonProperties(
                false,
                false,
                "runpod",
                "https://api.runpod.ai/v2/endpoint-xyz",
                "rp-secret-key",
                "/run",
                "/status/{id}",
                10,
                180,
                30,
                retryEnabled,
                delayMs,
                5);
        return new RunPodVtonWorkerClient(
                builder.build(),
                properties,
                millis -> sleepCalls.incrementAndGet());
    }
}
