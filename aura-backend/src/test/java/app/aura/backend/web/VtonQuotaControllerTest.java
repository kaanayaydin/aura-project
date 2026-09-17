package app.aura.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.aura.backend.model.User;
import app.aura.backend.model.WardrobeItem;
import app.aura.backend.repository.UserRepository;
import app.aura.backend.security.JwtService;
import app.aura.backend.service.VtonQuotaService;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * VTON gunluk kota — 5 deneme / 429 / iade (v0.19.1).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "aura.vton.daily-limit=5")
class VtonQuotaControllerTest {

    private static final String PNG = Base64.getEncoder().encodeToString(new byte[] {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01
    });

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Test
    void sixthVtonRequestReturns429() throws Exception {
        User user = new User("vton-quota", "vton-quota@aura.app");
        user.addWardrobeItem(new WardrobeItem("dress", 0.9, PNG, "image/png", "black"));
        user = userRepository.save(user);
        Long itemId = user.getWardrobeItems().getFirst().getId();
        String token = "Bearer " + jwtService.issueToken(user.getId(), user.getUsername());

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/aura/vton/request")
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"wardrobeItemId": %d}
                                    """.formatted(itemId)))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(post("/api/v1/aura/vton/request")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"wardrobeItemId": %d}
                                """.formatted(itemId)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.title").value("VTON kota limiti"))
                .andExpect(jsonPath("$.detail").value(VtonQuotaService.DAILY_LIMIT_MESSAGE));

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.getDailyVtonCount()).isEqualTo(5);
    }
}
