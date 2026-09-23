package app.aura.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.aura.backend.AuraBackendApplication;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Bos / kisa JWT secret ile context acilmamali. application.yml varsayilan secret tutmaz.
 */
class JwtSecretMissingContextTest {

    @Test
    void applicationYmlDoesNotEmbedJwtSecretDefault() throws Exception {
        String yml = Files.readString(Path.of("src/main/resources/application.yml"));
        assertThat(yml).contains("secret: ${AURA_JWT_SECRET}");
        assertThat(yml).doesNotContain("aura-dev-jwt-secret-change-me");
    }

    @Test
    void applicationContextDoesNotStartWhenJwtSecretBlank() {
        assertThatThrownBy(() -> {
            ConfigurableApplicationContext context = new SpringApplicationBuilder(AuraBackendApplication.class)
                    .profiles("test")
                    .run("--server.port=0", "--aura.security.jwt.secret=too-short");
            context.close();
        }).satisfies(thrown -> assertThat(rootMessages(thrown)).contains("AURA_JWT_SECRET"));
    }

    private static String rootMessages(Throwable thrown) {
        StringBuilder text = new StringBuilder();
        Throwable current = thrown;
        while (current != null) {
            text.append(current.getMessage()).append('\n');
            current = current.getCause();
        }
        return text.toString();
    }
}
