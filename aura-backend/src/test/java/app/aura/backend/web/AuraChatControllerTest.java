package app.aura.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.aura.backend.engine.WardrobeGuardrail;
import app.aura.backend.model.User;
import app.aura.backend.model.UserPerfume;
import app.aura.backend.model.WardrobeItem;
import app.aura.backend.repository.UserRepository;
import app.aura.backend.security.JwtService;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * POST /api/v1/aura/chat — JWT + fallback (v0.17.3).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuraChatControllerTest {

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
    void unauthenticatedChatReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/aura/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"Bugun ne giysem?"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Kimlik dogrulamasi gerekli"));
    }

    @Test
    void chatFallsBackWithWardrobeAndWeatherContext() throws Exception {
        User user = new User("chat-demo", "chat-demo@aura.app");
        user.addWardrobeItem(new WardrobeItem("shirt", 0.9, PNG_BASE64, "image/png", "navy"));
        user.addWardrobeItem(new WardrobeItem("pants", 0.88, PNG_BASE64, "image/png", "black"));
        user.addPerfume(new UserPerfume(
                "adp-colonia",
                "Acqua di Parma",
                "Colonia",
                "EDC",
                "citrus,fresh",
                "bergamot",
                "light"));
        user = userRepository.save(user);

        String body = """
                {
                  "message": "Bugun ne giysem?",
                  "history": []
                }
                """;

        mockMvc.perform(post("/api/v1/aura/chat")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value(containsString("°C")))
                .andExpect(jsonPath("$.reply").value(containsString("Aura notu")))
                // Eskiden "Kombin (yalnızca dolap listesinden):" iç kural metni bekleniyordu;
                // kullanıcıya iç kural gösterilmemesi için yerini doğal bir cümle aldı.
                .andExpect(jsonPath("$.reply").value(containsString("Kombin önerim:")))
                .andExpect(jsonPath("$.reply").value(not(containsString("dolap listesinden"))))
                .andExpect(jsonPath("$.reply").value(not(containsString("uydurma"))))
                .andExpect(jsonPath("$.source").value("fallback"))
                .andExpect(jsonPath("$.wardrobeCount").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.perfumeCount").value(1))
                .andExpect(jsonPath("$.weatherSummary").value(containsString("Istanbul")))
                .andExpect(jsonPath("$.weatherSummary").value(containsString("°C")))
                .andExpect(jsonPath("$.weatherSummary").value(not(containsString("open-meteo"))))
                .andExpect(jsonPath("$.weatherSummary").value(not(containsString("simulated"))))
                .andExpect(jsonPath("$.weatherSummary").value(not(containsString("Partly cloudy"))))
                .andExpect(jsonPath("$.weatherSummary").value(not(containsString("Clear"))))
                .andExpect(jsonPath("$.model").value("aura-fallback"));
    }

    @Test
    void fallbackForColorlessWardrobeIsPlainAndHasNoRepeats() throws Exception {
        User user = new User("chat-real", "chat-real@aura.app");
        for (int i = 0; i < 14; i++) {
            user.addWardrobeItem(new WardrobeItem("shirt", 0.9, PNG_BASE64, "image/png", null));
        }
        for (int i = 0; i < 4; i++) {
            user.addWardrobeItem(new WardrobeItem("pants", 0.9, PNG_BASE64, "image/png", null));
            user.addWardrobeItem(new WardrobeItem("t-shirt", 0.9, PNG_BASE64, "image/png", null));
        }
        user = userRepository.save(user);

        String body = mockMvc.perform(post("/api/v1/aura/chat")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Bugün ne giysem?\",\"history\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("fallback"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String reply = JsonPath.read(body, "$.reply");

        assertThat(reply)
                .doesNotContain("seçilmiş")
                .doesNotContain("uydurma")
                .doesNotContain("yalnızca dolap listesinden")
                .doesNotContain("Parfüm önermiyorum")
                .doesNotContainPattern("(?iu)(?<!\\p{L})siz(in)?(?!\\p{L})")
                .contains("Kombin önerim: **gömlek** + **pantolon**.");
        assertThat(reply.split(Pattern.quote("**gömlek**"), -1)).hasSize(2);
        assertThat(reply.split(Pattern.quote(WardrobeGuardrail.EMPTY_SHELF_LINE), -1)).hasSize(2);
    }

    @Test
    void chatRejectsBlankMessage() throws Exception {
        User user = userRepository.save(new User("chat-blank", "chat-blank@aura.app"));
        mockMvc.perform(post("/api/v1/aura/chat")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }
}
