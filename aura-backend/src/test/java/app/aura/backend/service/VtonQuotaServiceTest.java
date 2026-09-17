package app.aura.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.aura.backend.config.VtonProperties;
import app.aura.backend.model.User;
import app.aura.backend.repository.UserRepository;
import app.aura.backend.web.VtonQuotaExceededException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "aura.vton.daily-limit=5")
@Import(VtonQuotaServiceTest.MutableClockConfig.class)
class VtonQuotaServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");
    private static final AtomicReference<Instant> NOW =
            new AtomicReference<>(Instant.parse("2026-09-13T10:00:00Z"));

    @Autowired
    private VtonQuotaService vtonQuotaService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VtonProperties vtonProperties;

    @BeforeEach
    void setUp() {
        NOW.set(Instant.parse("2026-09-13T10:00:00Z"));
    }

    @Test
    void fifthConsumeSucceedsSixthThrows429Message() {
        assertThat(vtonProperties.dailyLimit()).isEqualTo(5);
        User user = userRepository.save(new User("quota-5", "quota-5@aura.app"));

        for (int i = 0; i < 5; i++) {
            vtonQuotaService.consume(user);
            user = userRepository.findById(user.getId()).orElseThrow();
        }
        assertThat(user.getDailyVtonCount()).isEqualTo(5);

        User finalUser = user;
        assertThatThrownBy(() -> vtonQuotaService.consume(finalUser))
                .isInstanceOf(VtonQuotaExceededException.class)
                .hasMessage(VtonQuotaService.DAILY_LIMIT_MESSAGE);
    }

    @Test
    void refundRestoresOneSlot() {
        User user = userRepository.save(new User("quota-ref", "quota-ref@aura.app"));
        vtonQuotaService.consume(user);
        user = userRepository.findById(user.getId()).orElseThrow();
        assertThat(user.getDailyVtonCount()).isEqualTo(1);

        vtonQuotaService.refund(user);
        user = userRepository.findById(user.getId()).orElseThrow();
        assertThat(user.getDailyVtonCount()).isEqualTo(0);
    }

    @Test
    void newDayResetsCounter() {
        User user = userRepository.save(new User("quota-day", "quota-day@aura.app"));
        for (int i = 0; i < 5; i++) {
            vtonQuotaService.consume(user);
            user = userRepository.findById(user.getId()).orElseThrow();
        }
        assertThat(user.getDailyVtonCount()).isEqualTo(5);

        NOW.set(Instant.parse("2026-09-14T08:00:00Z"));
        vtonQuotaService.consume(user);
        user = userRepository.findById(user.getId()).orElseThrow();

        assertThat(user.getLastVtonResetDate()).isEqualTo(LocalDate.of(2026, 9, 14));
        assertThat(user.getDailyVtonCount()).isEqualTo(1);
    }

    @TestConfiguration
    static class MutableClockConfig {
        @Bean
        @Primary
        Clock testClock() {
            return new Clock() {
                @Override
                public ZoneId getZone() {
                    return ZONE;
                }

                @Override
                public Clock withZone(ZoneId zone) {
                    return this;
                }

                @Override
                public Instant instant() {
                    return NOW.get();
                }
            };
        }
    }
}
