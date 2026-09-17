package app.aura.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Spring context'in ve JPA eslemelerinin sorunsuz yuklendigini dogrular.
 */
@SpringBootTest
@ActiveProfiles("test")
class AuraBackendApplicationTests {

    @Test
    void contextLoads() {
        // Context yuklenemezse bu test basarisiz olur.
    }
}
