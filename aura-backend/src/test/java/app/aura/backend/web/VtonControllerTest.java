package app.aura.backend.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.aura.backend.model.User;
import app.aura.backend.model.WardrobeItem;
import app.aura.backend.repository.UserRepository;
import app.aura.backend.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * VTON + JWT sahiplik — Faz 0.17.1.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VtonControllerTest {

    private static final String PNG = Base64.getEncoder().encodeToString(new byte[] {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01
    });

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    private String bearer(User user) {
        return "Bearer " + jwtService.issueToken(user.getId(), user.getUsername());
    }

    @Test
    void unauthenticatedVtonRequestReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/aura/vton/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"wardrobeItemId\":1}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Kimlik dogrulamasi gerekli"));
    }

    @Test
    void unauthenticatedResultImageReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/aura/vton/results/{jobId}/image", 1))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void lookbookSaveAndListViaJwt() throws Exception {
        User user = new User("vton-look", "vton-look@aura.app");
        user.addWardrobeItem(new WardrobeItem("dress", 0.95, PNG, "image/png", "black"));
        user = userRepository.save(user);
        Long itemId = user.getWardrobeItems().getFirst().getId();
        String token = bearer(user);

        String created = mockMvc.perform(post("/api/v1/aura/vton/request")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "wardrobeItemId": %d
                                }
                                """.formatted(itemId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long jobId = objectMapper.readTree(created).get("jobId").asLong();

        mockMvc.perform(get("/api/v1/aura/vton/status/{jobId}", jobId)
                .header(HttpHeaders.AUTHORIZATION, token));
        mockMvc.perform(get("/api/v1/aura/vton/status/{jobId}", jobId)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.lookbookSaved").value(false));

        mockMvc.perform(get("/api/v1/aura/vton/lookbook")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(post("/api/v1/aura/vton/lookbook/{jobId}", jobId)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobId))
                .andExpect(jsonPath("$.category").value("dress"))
                .andExpect(jsonPath("$.lookbookSaved").value(true));

        mockMvc.perform(get("/api/v1/aura/vton/history")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].jobId").value(jobId));

        mockMvc.perform(get("/api/v1/aura/vton/results/{jobId}/image", jobId)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());
    }

    @Test
    void resultImageForbiddenForOtherUserJwt() throws Exception {
        User owner = new User("vton-hdr-own", "vton-hdr-own@aura.app");
        owner.addWardrobeItem(new WardrobeItem("pants", 0.9, PNG, "image/png", "olive"));
        owner = userRepository.save(owner);
        Long itemId = owner.getWardrobeItems().getFirst().getId();
        String ownerToken = bearer(owner);

        String created = mockMvc.perform(post("/api/v1/aura/vton/request")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "wardrobeItemId": %d
                                }
                                """.formatted(itemId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long jobId = objectMapper.readTree(created).get("jobId").asLong();

        mockMvc.perform(get("/api/v1/aura/vton/status/{jobId}", jobId)
                .header(HttpHeaders.AUTHORIZATION, ownerToken));
        mockMvc.perform(get("/api/v1/aura/vton/status/{jobId}", jobId)
                        .header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        User stranger = userRepository.save(new User("vton-hdr-str", "vton-hdr-str@aura.app"));
        mockMvc.perform(get("/api/v1/aura/vton/results/{jobId}/image", jobId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(stranger)))
                .andExpect(status().isForbidden());
    }

    @Test
    void requestQueuesJobAndStatusAdvancesToCompleted() throws Exception {
        User user = new User("vton-flow", "vton-flow@aura.app");
        user.addWardrobeItem(new WardrobeItem("t-shirt", 0.9, PNG, "image/png", "navy"));
        user = userRepository.save(user);
        Long itemId = user.getWardrobeItems().getFirst().getId();
        String token = bearer(user);

        String created = mockMvc.perform(post("/api/v1/aura/vton/request")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "wardrobeItemId": %d
                                }
                                """.formatted(itemId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.jobId").exists())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.workerJobId").value(containsString("mock-")))
                .andExpect(jsonPath("$.wardrobeItemId").value(itemId))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long jobId = objectMapper.readTree(created).get("jobId").asLong();

        mockMvc.perform(get("/api/v1/aura/vton/status/{jobId}", jobId)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING"));

        mockMvc.perform(get("/api/v1/aura/vton/status/{jobId}", jobId)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.resultImageUri").value(containsString("mock://vton/result/")))
                .andExpect(jsonPath("$.resultImageUrl").value("/api/v1/aura/vton/results/" + jobId + "/image"))
                .andExpect(jsonPath("$.resultImageBase64").exists());

        mockMvc.perform(get("/api/v1/aura/vton/results/{jobId}/image", jobId)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header()
                        .string("Content-Type", containsString("image/")));
    }

    @Test
    void resultImageForbiddenForOtherUser() throws Exception {
        User owner = new User("vton-img-own", "vton-img-own@aura.app");
        owner.addWardrobeItem(new WardrobeItem("shirt", 0.9, PNG, "image/png", "white"));
        owner = userRepository.save(owner);
        Long itemId = owner.getWardrobeItems().getFirst().getId();
        String ownerToken = bearer(owner);

        String created = mockMvc.perform(post("/api/v1/aura/vton/request")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "wardrobeItemId": %d
                                }
                                """.formatted(itemId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long jobId = objectMapper.readTree(created).get("jobId").asLong();

        mockMvc.perform(get("/api/v1/aura/vton/status/{jobId}", jobId)
                .header(HttpHeaders.AUTHORIZATION, ownerToken));
        mockMvc.perform(get("/api/v1/aura/vton/status/{jobId}", jobId)
                        .header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        User stranger = userRepository.save(new User("vton-img-str", "vton-img-str@aura.app"));
        mockMvc.perform(get("/api/v1/aura/vton/results/{jobId}/image", jobId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(stranger)))
                .andExpect(status().isForbidden());
    }

    @Test
    void requestForbiddenWhenItemBelongsToAnotherUser() throws Exception {
        User owner = new User("vton-own", "vton-own@aura.app");
        owner.addWardrobeItem(new WardrobeItem("jacket", 0.9, PNG, "image/png", "black"));
        owner = userRepository.save(owner);
        Long itemId = owner.getWardrobeItems().getFirst().getId();

        User stranger = userRepository.save(new User("vton-str", "vton-str@aura.app"));

        mockMvc.perform(post("/api/v1/aura/vton/request")
                        .header(HttpHeaders.AUTHORIZATION, bearer(stranger))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "wardrobeItemId": %d
                                }
                                """.formatted(itemId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("VTON erisim engeli"));
    }

    @Test
    void requestNotFoundWhenWardrobeItemMissing() throws Exception {
        User user = userRepository.save(new User("vton-404", "vton-404@aura.app"));

        mockMvc.perform(post("/api/v1/aura/vton/request")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "wardrobeItemId": 999888
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void statusNotFoundForUnknownJob() throws Exception {
        User user = userRepository.save(new User("vton-job-miss", "vton-job-miss@aura.app"));

        mockMvc.perform(get("/api/v1/aura/vton/status/{jobId}", 424242)
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isNotFound());
    }

    @Test
    void requestRejectsMissingWardrobeItemId() throws Exception {
        User user = userRepository.save(new User("vton-bad", "vton-bad@aura.app"));
        mockMvc.perform(post("/api/v1/aura/vton/request")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void headerUserIdIgnoredInFavorOfJwtPrincipal() throws Exception {
        User owner = new User("vton-jwt-own", "vton-jwt-own@aura.app");
        owner.addWardrobeItem(new WardrobeItem("hoodie", 0.9, PNG, "image/png", "gray"));
        owner = userRepository.save(owner);
        Long itemId = owner.getWardrobeItems().getFirst().getId();

        User impostor = userRepository.save(new User("vton-jwt-imp", "vton-jwt-imp@aura.app"));

        // Impostor JWT + X-Aura-User-Id=owner → kimlik JWT'den gelir, item sahiplik 403
        mockMvc.perform(post("/api/v1/aura/vton/request")
                        .header(HttpHeaders.AUTHORIZATION, bearer(impostor))
                        .header("X-Aura-User-Id", owner.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "wardrobeItemId": %d,
                                  "userId": %d
                                }
                                """.formatted(itemId, owner.getId())))
                .andExpect(status().isForbidden());
    }
}
