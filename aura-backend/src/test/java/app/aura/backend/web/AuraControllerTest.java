package app.aura.backend.web;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.aura.backend.model.User;
import app.aura.backend.repository.UserRepository;
import app.aura.backend.security.JwtService;
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
 * POST /api/v1/aura/suggest — JWT kimlik (v0.17.3).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuraControllerTest {

    private static final byte[] PNG_BYTES = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01, 0x02, 0x03
    };
    private static final String PNG_BASE64 = Base64.getEncoder().encodeToString(PNG_BYTES);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    private String bearer(User user) {
        return "Bearer " + jwtService.issueToken(user.getId(), user.getUsername());
    }

    @Test
    void unauthenticatedSuggestReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/aura/suggest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"occasion":"casual","temperatureCelsius":20}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Kimlik dogrulamasi gerekli"));
    }

    @Test
    void suggestsOutfitForMeetingInMildWeather() throws Exception {
        User user = userRepository.save(new User("aura-meet", "aura-meet@aura.app"));
        String token = bearer(user);
        seedItem(user, "shirt", 0.9);
        seedItem(user, "pants", 0.88);
        seedItem(user, "watch", 0.7);
        seedItem(user, "t-shirt", 0.95);
        seedItem(user, "sneakers", 0.9);

        String body = """
                {
                  "temperatureCelsius": 18.5,
                  "humidityPercent": 55,
                  "occasion": "meeting"
                }
                """;

        mockMvc.perform(post("/api/v1/aura/suggest")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(user.getId()))
                .andExpect(jsonPath("$.vibe").exists())
                .andExpect(jsonPath("$.summary").exists())
                .andExpect(jsonPath("$.context.occasion").value("meeting"))
                .andExpect(jsonPath("$.context.seasonBand").value("mild"))
                .andExpect(jsonPath("$.top.item.category").value("shirt"))
                .andExpect(jsonPath("$.bottom.item.category").value("pants"))
                .andExpect(jsonPath("$.accessory.item.category").value("watch"))
                .andExpect(jsonPath("$.matchScore").value(greaterThan(0.5)))
                .andExpect(jsonPath("$.colorHarmony.type").exists())
                .andExpect(jsonPath("$.colorHarmony.score").exists())
                .andExpect(jsonPath("$.perfumeRecommendation.brand").exists())
                .andExpect(jsonPath("$.perfumeRecommendation.name").exists())
                .andExpect(jsonPath("$.perfumeRecommendation.chords").isArray())
                .andExpect(jsonPath("$.perfumeRecommendation.topNotes").isArray())
                .andExpect(jsonPath("$.perfumeRecommendation.baseNotes").isArray())
                .andExpect(jsonPath("$.perfumeRecommendation.score").value(greaterThan(0.4)))
                .andExpect(jsonPath("$.top.item.imageBase64").doesNotExist());
    }

    @Test
    void bodyUserIdIsIgnoredInFavorOfJwtPrincipal() throws Exception {
        User owner = userRepository.save(new User("sug-own", "sug-own@aura.app"));
        User impostor = userRepository.save(new User("sug-imp", "sug-imp@aura.app"));
        seedItem(owner, "shirt", 0.9);
        seedItem(owner, "pants", 0.9);

        mockMvc.perform(post("/api/v1/aura/suggest")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": %d,
                                  "temperatureCelsius": 20,
                                  "occasion": "casual"
                                }
                                """.formatted(impostor.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(owner.getId()));
    }

    @Test
    void prefersSportFriendlyPiecesWhenHot() throws Exception {
        User user = userRepository.save(new User("aura-sport", "aura-sport@aura.app"));
        seedItem(user, "jacket", 0.99);
        seedItem(user, "t-shirt", 0.80);
        seedItem(user, "pants", 0.85);
        seedItem(user, "sneakers", 0.90);

        mockMvc.perform(post("/api/v1/aura/suggest")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "temperatureCelsius": 29,
                                  "humidityPercent": 80,
                                  "occasion": "sport",
                                  "includeImages": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.top.item.category").value("t-shirt"))
                .andExpect(jsonPath("$.accessory.item.category").value("sneakers"))
                .andExpect(jsonPath("$.top.item.imageUrl").exists())
                .andExpect(jsonPath("$.context.seasonBand").value("hot"))
                .andExpect(jsonPath("$.perfumeRecommendation.diffusion").exists())
                .andExpect(jsonPath("$.perfumeRecommendation.thermodynamicNote").exists());
    }

    @Test
    void rejectsUnknownOccasion() throws Exception {
        User user = userRepository.save(new User("aura-bad-occ", "aura-bad@aura.app"));
        seedItem(user, "t-shirt", 0.8);

        mockMvc.perform(post("/api/v1/aura/suggest")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"temperatureCelsius": 20, "occasion": "gala"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Gecersiz etkinlik baglami"));
    }

    @Test
    void returns422WhenWardrobeEmpty() throws Exception {
        User user = userRepository.save(new User("aura-empty", "aura-empty@aura.app"));

        mockMvc.perform(post("/api/v1/aura/suggest")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"temperatureCelsius": 20, "occasion": "casual"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Dolap yetersiz"));
    }

    @Test
    void usesAutoWeatherWhenTemperatureOmitted() throws Exception {
        User user = userRepository.save(new User("auto-hava", "auto-hava@aura.app"));
        seedItem(user, "t-shirt", 0.9);
        seedItem(user, "pants", 0.85);

        mockMvc.perform(post("/api/v1/aura/suggest")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"occasion": "casual", "useAutoWeather": true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.context.autoWeather").value(true))
                .andExpect(jsonPath("$.context.weatherSource").value("simulated"))
                .andExpect(jsonPath("$.context.weatherCondition").exists())
                .andExpect(jsonPath("$.context.temperatureCelsius").exists());
    }

    @Test
    void weatherCurrentEndpointReturnsSnapshot() throws Exception {
        mockMvc.perform(get("/api/v1/weather/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temperatureCelsius").exists())
                .andExpect(jsonPath("$.humidityPercent").exists())
                .andExpect(jsonPath("$.condition").exists())
                .andExpect(jsonPath("$.source").value("simulated"))
                .andExpect(jsonPath("$.locationName").value("Istanbul"));
    }

    private void seedItem(User user, String category, double confidence) throws Exception {
        String body = """
                {
                  "category": "%s",
                  "categoryConfidence": %s,
                  "imageBase64": "%s"
                }
                """.formatted(category, confidence, PNG_BASE64);

        mockMvc.perform(post("/api/v1/wardrobe/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }
}
