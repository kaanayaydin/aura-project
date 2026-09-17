package app.aura.backend.web;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.aura.backend.model.AccountStatus;
import app.aura.backend.model.User;
import app.aura.backend.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Gercek auth akisi — register/login/refresh/logout + blacklist + brute-force (v0.18.1).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

    private static final String STRONG_PASSWORD = "Secret123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Test
    void registerLoginProtectedRefreshLogoutAndDetectRefreshReuse() throws Exception {
        String email = "real-auth-" + System.nanoTime() + "@aura.app";

        mockMvc.perform(post("/api/v1/aura/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, STRONG_PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.userId").exists());

        MvcResult loginResult = mockMvc.perform(post("/api/v1/aura/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, STRONG_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.email").value(email))
                .andReturn();

        JsonNode login = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String access = login.get("accessToken").asText();
        String refresh = login.get("refreshToken").asText();

        mockMvc.perform(get("/api/v1/wardrobe/items")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        MvcResult refreshResult = mockMvc.perform(post("/api/v1/aura/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refresh)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").value(not(refresh)))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andReturn();

        JsonNode refreshed = objectMapper.readTree(refreshResult.getResponse().getContentAsString());
        String newAccess = refreshed.get("accessToken").asText();
        String newRefresh = refreshed.get("refreshToken").asText();

        mockMvc.perform(post("/api/v1/aura/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + newAccess)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(newRefresh)))
                .andExpect(status().isNoContent());

        // Access token blacklist — ayni JWT ile korumali endpoint 401
        mockMvc.perform(get("/api/v1/wardrobe/items")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + newAccess))
                .andExpect(status().isUnauthorized());

        // Eski (rotate edilmis) refresh → hirsizlik / replay tespiti → 401
        mockMvc.perform(post("/api/v1/aura/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refresh)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Kimlik dogrulamasi gerekli"));
    }

    @Test
    void logoutBlacklistsAccessTokenUntilExpiry() throws Exception {
        String email = "blacklist-" + System.nanoTime() + "@aura.app";
        registerAndLogin(email, STRONG_PASSWORD);

        MvcResult loginResult = mockMvc.perform(post("/api/v1/aura/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, STRONG_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode login = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String access = login.get("accessToken").asText();
        String refresh = login.get("refreshToken").asText();

        mockMvc.perform(get("/api/v1/wardrobe/items")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/aura/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refresh)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/wardrobe/items")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Kimlik dogrulamasi gerekli"));
    }

    @Test
    void fiveFailedLoginsLockAccountEvenWithCorrectPassword() throws Exception {
        String email = "lock-me-" + System.nanoTime() + "@aura.app";
        String password = "ValidPass1!";

        mockMvc.perform(post("/api/v1/aura/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isCreated());

        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/v1/aura/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"%s","password":"WrongPass9"}
                                    """.formatted(email)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("Email veya sifre hatali."));
        }

        mockMvc.perform(post("/api/v1/aura/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"WrongPass9"}
                                """.formatted(email)))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.title").value("Hesap kilitli"));

        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.LOCKED);
        org.assertj.core.api.Assertions.assertThat(user.getFailedLoginAttempts()).isGreaterThanOrEqualTo(5);
        org.assertj.core.api.Assertions.assertThat(user.getLockedUntil()).isNotNull();
        org.assertj.core.api.Assertions.assertThat(user.getLockedUntil())
                .isAfter(java.time.Instant.now().plusSeconds(14 * 60));

        // Dogru sifre bile kilitliyken 423 — lockedUntil dolmadan acilmaz
        mockMvc.perform(post("/api/v1/aura/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.title").value("Hesap kilitli"));

        user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.LOCKED);
    }

    @Test
    void registerRejectsWeakPasswords() throws Exception {
        mockMvc.perform(post("/api/v1/aura/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"weak1@aura.app","password":"nodigit!"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());

        mockMvc.perform(post("/api/v1/aura/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"weak2@aura.app","password":"NoSpecial1"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());

        mockMvc.perform(post("/api/v1/aura/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"weak3@aura.app","password":"alllower1!"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void registerRejectsInvalidAndDisposableEmails() throws Exception {
        mockMvc.perform(post("/api/v1/aura/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","password":"%s"}
                                """.formatted(STRONG_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists());

        mockMvc.perform(post("/api/v1/aura/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@mailinator.com","password":"%s"}
                                """.formatted(STRONG_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test
    void deprecatedDemoTokenStillWorks() throws Exception {
        mockMvc.perform(post("/api/v1/aura/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    private void registerAndLogin(String email, String password) throws Exception {
        mockMvc.perform(post("/api/v1/aura/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isCreated());
    }
}
