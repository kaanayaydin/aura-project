package app.aura.backend.web;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.aura.backend.model.User;
import app.aura.backend.model.VtonJob;
import app.aura.backend.model.WardrobeItem;
import app.aura.backend.repository.UserRepository;
import app.aura.backend.repository.VtonJobRepository;
import app.aura.backend.security.JwtService;
import app.aura.backend.support.PinnedHttpDownloader;
import app.aura.backend.support.TinyHttpServer;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VtonResultSsrfControllerTest {

    private static final String PNG = Base64.getEncoder().encodeToString(new byte[] {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01
    });

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VtonJobRepository vtonJobRepository;

    @Autowired
    private JwtService jwtService;

    @MockitoSpyBean
    private PinnedHttpDownloader pinnedHttpDownloader;

    @Test
    void metadataResultUriRejectedWithoutDownload() throws Exception {
        User user = userRepository.save(new User("vton-res-ssrf", "vton-res-ssrf@aura.app"));
        user.addWardrobeItem(new WardrobeItem("shirt", 0.9, PNG, "image/png", "navy"));
        user = userRepository.save(user);
        Long itemId = user.getWardrobeItems().getFirst().getId();
        VtonJob job = new VtonJob(user, itemId, PNG);
        job.markCompleted("http://169.254.169.254/latest/meta-data/", null);
        job = vtonJobRepository.save(job);
        String token = "Bearer " + jwtService.issueToken(user.getId(), user.getUsername());

        mockMvc.perform(get("/api/v1/aura/vton/results/{jobId}/image", job.getId())
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.rejected_reason").value("unsafe_url"));
    }

    @Test
    void workerInternalResultUriRejected() throws Exception {
        User user = userRepository.save(new User("vton-res-int", "vton-res-int@aura.app"));
        user.addWardrobeItem(new WardrobeItem("shirt", 0.9, PNG, "image/png", "navy"));
        user = userRepository.save(user);
        Long itemId = user.getWardrobeItems().getFirst().getId();
        VtonJob job = new VtonJob(user, itemId, PNG);
        job.markCompleted("http://127.0.0.1:8001/internal", null);
        job = vtonJobRepository.save(job);
        String token = "Bearer " + jwtService.issueToken(user.getId(), user.getUsername());

        mockMvc.perform(get("/api/v1/aura/vton/results/{jobId}/image", job.getId())
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.rejected_reason").value("unsafe_url"));
    }

    @Test
    void unlistedPngServerResultUriNeverContacted() throws Exception {
        byte[] png = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01, 0x02, 0x03
        };
        try (TinyHttpServer server = new TinyHttpServer(png)) {
            User user = userRepository.save(new User("vton-res-fake", "vton-res-fake@aura.app"));
            user.addWardrobeItem(new WardrobeItem("shirt", 0.9, PNG, "image/png", "navy"));
            user = userRepository.save(user);
            Long itemId = user.getWardrobeItems().getFirst().getId();
            VtonJob job = new VtonJob(user, itemId, PNG);
            job.markCompleted("http://127.0.0.1:" + server.port() + "/steal.png", null);
            job = vtonJobRepository.save(job);
            String token = "Bearer " + jwtService.issueToken(user.getId(), user.getUsername());

            mockMvc.perform(get("/api/v1/aura/vton/results/{jobId}/image", job.getId())
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isForbidden());

            org.assertj.core.api.Assertions.assertThat(server.hits()).isZero();
            verify(pinnedHttpDownloader, never()).download(anyString(), anyInt());
        }
    }
}
