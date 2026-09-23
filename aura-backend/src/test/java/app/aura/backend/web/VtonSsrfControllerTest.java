package app.aura.backend.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.aura.backend.model.User;
import app.aura.backend.model.WardrobeItem;
import app.aura.backend.repository.UserRepository;
import app.aura.backend.security.JwtService;
import app.aura.backend.service.VtonQuotaService;
import app.aura.backend.service.VtonWorkerClient;
import app.aura.backend.support.PngBombs;
import app.aura.backend.support.StorageUrlGuard;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * SSRF allowlist + kota consume spy (rollback sayacina guvenilmez).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VtonSsrfControllerTest {

    private static final String PNG = Base64.getEncoder().encodeToString(new byte[] {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01
    });

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @MockitoSpyBean
    private VtonQuotaService vtonQuotaService;

    @MockitoSpyBean
    private VtonWorkerClient vtonWorkerClient;

    @MockitoSpyBean
    private StorageUrlGuard storageUrlGuard;

    @BeforeEach
    void resetSpies() {
        reset(vtonQuotaService, vtonWorkerClient, storageUrlGuard);
    }

    @Test
    void metadataUrlRejectedBeforeConsumeAndEnqueue() throws Exception {
        assertUrlBlocked("http://169.254.169.254/latest/meta-data/");
    }

    @Test
    void loopbackWorkerUrlRejectedBeforeConsumeAndEnqueue() throws Exception {
        assertUrlBlocked("http://127.0.0.1:8001/internal");
    }

    @Test
    void externalIpBombUrlRejectedBeforeConsumeAndEnqueue() throws Exception {
        assertUrlBlocked("http://203.0.113.1/bomb.jpg");
    }

    @Test
    void allowlistedPresignedObjectUrlStillEnqueues() throws Exception {
        User user = new User("vton-s3-ok", "vton-s3-ok@aura.app");
        user.addWardrobeItem(new WardrobeItem("shirt", 0.9, PNG, "image/png", "navy"));
        user = userRepository.save(user);
        Long itemId = user.getWardrobeItems().getFirst().getId();
        String token = "Bearer " + jwtService.issueToken(user.getId(), user.getUsername());
        String objectUrl = "http://127.0.0.1:9000/memory/aura-vton/person/fixture.jpg";

        mockMvc.perform(post("/api/v1/aura/vton/request")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "wardrobeItemId": %d,
                                  "personImageUrl": "%s"
                                }
                                """.formatted(itemId, objectUrl)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.jobId").exists());

        verify(vtonQuotaService).consume(any());
        verify(vtonWorkerClient).enqueue(any());
    }

    @Test
    void unparseableOver24BytesReturns422AndNeverCallsConsume() throws Exception {
        User user = new User("vton-k8", "vton-k8@aura.app");
        user.addWardrobeItem(new WardrobeItem("shirt", 0.9, PNG, "image/png", "navy"));
        user = userRepository.save(user);
        Long itemId = user.getWardrobeItems().getFirst().getId();
        String token = "Bearer " + jwtService.issueToken(user.getId(), user.getUsername());
        byte[] garbage = PngBombs.unparseableGarbage();
        org.assertj.core.api.Assertions.assertThat(garbage.length).isGreaterThanOrEqualTo(24);
        String b64 = Base64.getEncoder().encodeToString(garbage);

        mockMvc.perform(post("/api/v1/aura/vton/request")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "wardrobeItemId": %d,
                                  "personImageBase64": "%s"
                                }
                                """.formatted(itemId, b64)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.rejected_reason").value("decode_failed"));

        verify(vtonQuotaService, never()).consume(any());
        verify(vtonWorkerClient, never()).enqueue(any());
    }

    @Test
    void garmentImageUrlMetadataRejectedBeforeConsumeAndEnqueue() throws Exception {
        User user = new User("vton-s2", "vton-s2@aura.app");
        WardrobeItem item = new WardrobeItem("shirt", 0.9, PNG, "image/png", "navy");
        item.setImageUrl("http://169.254.169.254/latest/meta-data/");
        user.addWardrobeItem(item);
        user = userRepository.save(user);
        Long itemId = user.getWardrobeItems().getFirst().getId();
        String token = "Bearer " + jwtService.issueToken(user.getId(), user.getUsername());

        mockMvc.perform(post("/api/v1/aura/vton/request")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "wardrobeItemId": %d,
                                  "personImageBase64": "%s"
                                }
                                """.formatted(itemId, PNG)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.rejected_reason").value("unsafe_url"));

        verify(storageUrlGuard).rejectUnsafeObjectUrl("http://169.254.169.254/latest/meta-data/");
        verify(vtonQuotaService, never()).consume(any());
        verify(vtonWorkerClient, never()).enqueue(any());
    }

    private void assertUrlBlocked(String personImageUrl) throws Exception {
        User user = new User("vton-ssrf-" + System.nanoTime(), "vton-ssrf-" + System.nanoTime() + "@aura.app");
        user.addWardrobeItem(new WardrobeItem("shirt", 0.9, PNG, "image/png", "navy"));
        user = userRepository.save(user);
        Long itemId = user.getWardrobeItems().getFirst().getId();
        String token = "Bearer " + jwtService.issueToken(user.getId(), user.getUsername());

        mockMvc.perform(post("/api/v1/aura/vton/request")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "wardrobeItemId": %d,
                                  "personImageUrl": "%s"
                                }
                                """.formatted(itemId, personImageUrl)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.rejected_reason").value("unsafe_url"));

        verify(vtonQuotaService, never()).consume(any());
        verify(vtonWorkerClient, never()).enqueue(any());
    }
}
