package app.aura.backend.web;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.aura.backend.model.User;
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
 * Favoriler — JWT kimlik (v0.17.2).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FavoriteOutfitControllerTest {

    private static final String PNG_BASE64 = Base64.getEncoder().encodeToString(new byte[] {
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
    void unauthenticatedFavoritesReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/aura/favorites"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Kimlik dogrulamasi gerekli"));

        mockMvc.perform(post("/api/v1/aura/favorites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"occasion":"casual","vibe":"x"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void saveListAndDeleteFavorite() throws Exception {
        User user = userRepository.save(new User("fav-user", "fav@aura.app"));
        String token = bearer(user);
        seedItem(user, "shirt", "white");
        seedItem(user, "pants", "navy");

        String suggestBody = """
                {
                  "userId": %d,
                  "temperatureCelsius": 18,
                  "humidityPercent": 50,
                  "occasion": "meeting"
                }
                """.formatted(user.getId());

        String suggestion = mockMvc.perform(post("/api/v1/aura/suggest")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(suggestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.colorHarmony.type").exists())
                .andExpect(jsonPath("$.colorHarmony.score").value(greaterThan(0.4)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        var tree = objectMapper.readTree(suggestion);
        Long topId = tree.path("top").path("item").path("id").asLong();
        Long bottomId = tree.path("bottom").path("item").path("id").asLong();

        String saveBody = """
                {
                  "vibe": "%s",
                  "summary": "%s",
                  "occasion": "meeting",
                  "temperatureCelsius": 18,
                  "seasonBand": "%s",
                  "matchScore": %s,
                  "colorHarmonyType": "%s",
                  "colorHarmonyScore": %s,
                  "topItemId": %d,
                  "bottomItemId": %d,
                  "perfumeCatalogId": "adp-colonia",
                  "perfumeLabel": "Acqua di Parma Colonia"
                }
                """.formatted(
                tree.path("vibe").asText(),
                tree.path("summary").asText().replace("\"", "'"),
                tree.path("context").path("seasonBand").asText(),
                tree.path("matchScore").asDouble(),
                tree.path("colorHarmony").path("type").asText(),
                tree.path("colorHarmony").path("score").asDouble(),
                topId,
                bottomId);

        String created = mockMvc.perform(post("/api/v1/aura/favorites")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.occasion").value("meeting"))
                .andExpect(jsonPath("$.topItemId").value(topId))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long favoriteId = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(get("/api/v1/aura/favorites")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(delete("/api/v1/aura/favorites/{id}", favoriteId)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/aura/favorites")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deleteForeignFavoriteReturns403() throws Exception {
        User owner = userRepository.save(new User("fav-own", "fav-own@aura.app"));
        User other = userRepository.save(new User("fav-oth", "fav-oth@aura.app"));
        String ownerToken = bearer(owner);

        String created = mockMvc.perform(post("/api/v1/aura/favorites")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "occasion": "casual",
                                  "vibe": "soft",
                                  "summary": "test"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long favoriteId = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(delete("/api/v1/aura/favorites/{id}", favoriteId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Favori erisim engeli"));
    }

    private void seedItem(User user, String category, String color) throws Exception {
        mockMvc.perform(post("/api/v1/wardrobe/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "category": "%s",
                                  "categoryConfidence": 0.9,
                                  "imageBase64": "%s",
                                  "color": "%s"
                                }
                                """.formatted(category, PNG_BASE64, color)))
                .andExpect(status().isCreated());
    }
}
