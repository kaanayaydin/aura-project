package app.aura.backend.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.aura.backend.model.User;
import app.aura.backend.repository.UserRepository;
import app.aura.backend.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Test
    void issueTokenForExistingUser() throws Exception {
        User user = userRepository.save(new User("auth-demo", "auth-demo@aura.app"));

        mockMvc.perform(post("/api/v1/aura/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId": %d}
                                """.formatted(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.userId").value(user.getId()))
                .andExpect(jsonPath("$.username").value("auth-demo"));

        // Token parse edilebilir
        String token = jwtService.issueToken(user.getId(), user.getUsername());
        var principal = jwtService.parse(token);
        org.assertj.core.api.Assertions.assertThat(principal.userId()).isEqualTo(user.getId());
    }
}
