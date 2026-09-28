package app.aura.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.aura.backend.dto.ChatTurn;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuraChatServiceHistoryTest {

    @Test
    void selectRecentHistoryKeepsTheLastTwelveInOrder() {
        List<ChatTurn> history = new ArrayList<>();
        for (int i = 1; i <= 13; i++) {
            history.add(new ChatTurn("user", "tur-" + i));
        }

        List<ChatTurn> recent = AuraChatService.selectRecentHistory(history);

        assertThat(recent).hasSize(12);
        assertThat(recent.getFirst().content()).isEqualTo("tur-2");
        assertThat(recent.getLast().content()).isEqualTo("tur-13");
        assertThat(recent).extracting(ChatTurn::content).doesNotContain("tur-1");
    }
}
