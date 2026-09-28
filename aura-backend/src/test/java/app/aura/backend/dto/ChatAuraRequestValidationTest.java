package app.aura.backend.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChatAuraRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void historyTurnOver8000CharactersIsRejected() {
        ChatAuraRequest request = new ChatAuraRequest(
                "Bugün ne giysem?",
                List.of(new ChatTurn("user", "x".repeat(8001))),
                null,
                null,
                null);

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void historyLongerThan100TurnsIsRejected() {
        List<ChatTurn> history = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            history.add(new ChatTurn(i % 2 == 0 ? "user" : "assistant", "tur"));
        }
        ChatAuraRequest request = new ChatAuraRequest(
                "Bugün ne giysem?",
                history,
                null,
                null,
                null);

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void historyWithinLimitsIsAccepted() {
        ChatAuraRequest request = new ChatAuraRequest(
                "Bugün ne giysem?",
                List.of(new ChatTurn("user", "x".repeat(8000))),
                null,
                null,
                null);

        assertThat(validator.validate(request)).isEmpty();
    }
}
