package app.aura.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.aura.backend.model.User;
import app.aura.backend.repository.UserRepository;
import app.aura.backend.repository.WardrobeItemRepository;
import app.aura.backend.security.JwtService;
import app.aura.backend.service.VisionGarmentClient;
import app.aura.backend.support.TinyHttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Wardrobe imageUrl SSRF — StorageUrlGuard + PinnedHttpDownloader.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WardrobeSsrfControllerTest {

    private static final byte[] PNG = {
        (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01, 0x02, 0x03
    };

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WardrobeItemRepository wardrobeItemRepository;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private VisionGarmentClient visionGarmentClient;

    @Test
    void metadataImageUrlRejectedWithoutSaving() throws Exception {
        assertImageUrlBlocked("http://169.254.169.254/latest/meta-data/");
    }

    @Test
    void loopbackWorkerImageUrlRejectedWithoutSaving() throws Exception {
        assertImageUrlBlocked("http://127.0.0.1:8001/internal");
    }

    @Test
    void fakePngServerOnUnlistedPortNeverContacted() throws Exception {
        try (TinyHttpServer server = new TinyHttpServer(PNG)) {
            User user = userRepository.save(new User("ward-ssrf-png", "ward-ssrf-png@aura.app"));
            String token = "Bearer " + jwtService.issueToken(user.getId(), user.getUsername());
            String url = "http://127.0.0.1:" + server.port() + "/secret.png";

            mockMvc.perform(post("/api/v1/wardrobe/items")
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"category":"shirt","imageUrl":"%s"}
                                    """.formatted(url)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.rejected_reason").value("unsafe_url"));

            assertThat(server.hits()).isZero();
            assertThat(wardrobeItemRepository.findByUserId(user.getId())).isEmpty();
            verify(visionGarmentClient, never()).normalizeGarmentPng(any(), any(), anyBoolean());
        }
    }

    private void assertImageUrlBlocked(String imageUrl) throws Exception {
        User user = userRepository.save(new User(
                "ward-ssrf-" + System.nanoTime(),
                "ward-ssrf-" + System.nanoTime() + "@aura.app"));
        String token = "Bearer " + jwtService.issueToken(user.getId(), user.getUsername());

        mockMvc.perform(post("/api/v1/wardrobe/items")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"category":"shirt","imageUrl":"%s"}
                                """.formatted(imageUrl)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.rejected_reason").value("unsafe_url"));

        assertThat(wardrobeItemRepository.findByUserId(user.getId())).isEmpty();
        verify(visionGarmentClient, never()).normalizeGarmentPng(any(), any(), anyBoolean());
    }
}
