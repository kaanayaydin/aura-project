package app.aura.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * HEAD semasi (issued_at yok) + dolu refresh_tokens satiri, sonra ddl-auto=update.
 * H2 create-drop bu ALTER hatasini gizler; Postgres gizlemez.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IssuedAtMigrationPostgresTest {

    private static final String EMAIL = "legacy-migrate@aura.app";
    private static final String PASSWORD = "Secret123!";
    private static final String LEGACY_HASH = "a".repeat(64);

    static {
        // Docker Engine 29, 1.44 altı /info isteğini 400 ile keser.
        System.setProperty("api.version", "1.44");
    }

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
        seedHeadSchemaWithRow();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginAfterAddingIssuedAtOnPopulatedTableIsNot500() throws Exception {
        try (Connection connection = open();
                ResultSet nullable = connection.createStatement().executeQuery("""
                        SELECT is_nullable
                        FROM information_schema.columns
                        WHERE table_name = 'refresh_tokens' AND column_name = 'issued_at'
                        """)) {
            assertThat(nullable.next()).isTrue();
            assertThat(nullable.getString(1)).isEqualTo("YES");
        }

        mockMvc.perform(post("/api/v1/aura/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString());

        try (Connection connection = open();
                PreparedStatement legacy = connection.prepareStatement(
                        "SELECT issued_at IS NULL FROM refresh_tokens WHERE token_hash = ?");
                PreparedStatement fresh = connection.prepareStatement(
                        "SELECT count(*) FROM refresh_tokens WHERE issued_at IS NOT NULL")) {
            legacy.setString(1, LEGACY_HASH);
            try (ResultSet rows = legacy.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getBoolean(1)).isTrue();
            }
            try (ResultSet rows = fresh.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isGreaterThanOrEqualTo(1);
            }
        }
    }

    private static void seedHeadSchemaWithRow() {
        String hash = new BCryptPasswordEncoder(12).encode(PASSWORD);
        try (Connection connection = open();
                var statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE users (
                        id bigserial PRIMARY KEY,
                        username varchar(120) NOT NULL UNIQUE,
                        email varchar(120) NOT NULL UNIQUE,
                        password_hash varchar(100),
                        email_verified boolean NOT NULL DEFAULT false,
                        account_status varchar(20) NOT NULL DEFAULT 'ACTIVE',
                        failed_login_attempts integer NOT NULL DEFAULT 0,
                        locked_until timestamp,
                        daily_vton_count integer NOT NULL DEFAULT 0,
                        last_vton_reset_date date
                    )
                    """);
            statement.execute("""
                    CREATE TABLE refresh_tokens (
                        id uuid PRIMARY KEY,
                        user_id bigint NOT NULL REFERENCES users(id),
                        token_hash varchar(64) NOT NULL UNIQUE,
                        expires_at timestamp NOT NULL,
                        revoked boolean NOT NULL DEFAULT false,
                        created_at timestamp NOT NULL
                    )
                    """);
            try (PreparedStatement insertUser = connection.prepareStatement("""
                    INSERT INTO users (
                        username, email, password_hash, email_verified, account_status,
                        failed_login_attempts, daily_vton_count)
                    VALUES ('legacy-user', ?, ?, false, 'ACTIVE', 0, 0)
                    """)) {
                insertUser.setString(1, EMAIL);
                insertUser.setString(2, hash);
                insertUser.executeUpdate();
            }
            try (PreparedStatement insertToken = connection.prepareStatement("""
                    INSERT INTO refresh_tokens (id, user_id, token_hash, expires_at, revoked, created_at)
                    VALUES (?, currval('users_id_seq'), ?, now() + interval '7 days', false, now())
                    """)) {
                insertToken.setObject(1, UUID.randomUUID());
                insertToken.setString(2, LEGACY_HASH);
                insertToken.executeUpdate();
            }
        } catch (Exception exception) {
            throw new IllegalStateException("HEAD semasi tohumlanamadi", exception);
        }
    }

    private static Connection open() throws Exception {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
