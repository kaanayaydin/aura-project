package app.aura.backend.web;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.aura.backend.model.User;
import app.aura.backend.repository.UserRepository;
import app.aura.backend.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Parfum rafi — JWT kimlik (v0.17.3).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserPerfumeControllerTest {

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
    void unauthenticatedPerfumeReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/user/perfumes/catalog"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Kimlik dogrulamasi gerekli"));

        mockMvc.perform(get("/api/v1/user/perfumes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void catalogListsCuratedPerfumes() throws Exception {
        User user = userRepository.save(new User("katalogcu", "katalog@aura.app"));
        mockMvc.perform(get("/api/v1/user/perfumes/catalog")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(10)))
                .andExpect(jsonPath("$[0].catalogId").exists())
                .andExpect(jsonPath("$[0].topNotes").isArray())
                .andExpect(jsonPath("$[0].onShelf").value(false));
    }

    @Test
    void addListAndRemoveFromShelf() throws Exception {
        User user = userRepository.save(new User("rafci", "rafci@aura.app"));
        String token = bearer(user);

        String created = mockMvc.perform(post("/api/v1/user/perfumes")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"catalogId": "adp-colonia"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.catalogId").value("adp-colonia"))
                .andExpect(jsonPath("$.brand").value("Acqua di Parma"))
                .andExpect(jsonPath("$.onShelf").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long perfumeId = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(get("/api/v1/user/perfumes")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Colonia"));

        mockMvc.perform(get("/api/v1/user/perfumes/catalog")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.catalogId=='adp-colonia')].onShelf")
                        .value(hasItem(true)));

        mockMvc.perform(post("/api/v1/user/perfumes")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"catalogId": "adp-colonia"}
                                """))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/v1/user/perfumes/{id}", perfumeId)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/user/perfumes")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deleteForeignPerfumeReturns403() throws Exception {
        User owner = userRepository.save(new User("perf-own", "perf-own@aura.app"));
        User other = userRepository.save(new User("perf-oth", "perf-oth@aura.app"));

        String created = mockMvc.perform(post("/api/v1/user/perfumes")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"catalogId": "adp-colonia"}
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long perfumeId = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(delete("/api/v1/user/perfumes/{id}", perfumeId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Parfum erisim engeli"));
    }

    @Test
    void rejectsUnknownCatalogId() throws Exception {
        User user = userRepository.save(new User("perf-miss", "perf-miss@aura.app"));
        mockMvc.perform(post("/api/v1/user/perfumes")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"catalogId": "yok-boyle-bir-parfum"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Parfum bulunamadi"));
    }
}
