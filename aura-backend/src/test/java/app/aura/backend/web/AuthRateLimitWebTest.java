package app.aura.backend.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "aura.security.auth.rate-limit-per-minute=2",
        "aura.security.auth.rate-limit-store=memory"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthRateLimitWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void thirdLoginFromSameIpReturns429ProblemDetail() throws Exception {
        String body = """
                {"email":"rate-limit@aura.app","password":"WrongPass9"}
                """;
        mockMvc.perform(post("/api/v1/aura/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/aura/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/aura/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.title").value("Cok fazla istek"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.detail").exists());
    }
}
