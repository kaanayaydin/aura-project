package app.aura.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import app.aura.backend.config.VtonProperties;
import app.aura.backend.dto.VtonRequest;
import app.aura.backend.model.User;
import app.aura.backend.model.VtonJob;
import app.aura.backend.model.VtonJobStatus;
import app.aura.backend.model.WardrobeItem;
import app.aura.backend.repository.UserRepository;
import app.aura.backend.repository.VtonJobRepository;
import app.aura.backend.web.VtonWorkerUnavailableException;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Worker basarisizliginda kota iadesi.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "aura.vton.daily-limit=5",
        "aura.vton.mock-worker-enabled=false",
        "aura.vton.mock-auto-advance-on-poll=false",
        "aura.vton.provider=local"
})
class VtonQuotaRefundIntegrationTest {

    private static final String PNG = Base64.getEncoder().encodeToString(new byte[] {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01
    });

    @Autowired
    private VtonService vtonService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VtonJobRepository vtonJobRepository;

    @MockitoBean
    private VtonWorkerClient vtonWorkerClient;

    @Test
    void workerUnavailableRefundsQuota() {
        User saved = new User("vton-refund", "vton-refund@aura.app");
        saved.addWardrobeItem(new WardrobeItem("coat", 0.9, PNG, "image/png", "beige"));
        final User user = userRepository.save(saved);
        final Long itemId = user.getWardrobeItems().getFirst().getId();
        final Long userId = user.getId();

        when(vtonWorkerClient.enqueue(any())).thenThrow(new VtonWorkerUnavailableException("cold-start timeout"));

        assertThatThrownBy(() -> vtonService.request(userId, new VtonRequest(itemId, null, PNG, null)))
                .isInstanceOf(VtonWorkerUnavailableException.class);

        User reloaded = userRepository.findById(userId).orElseThrow();
        assertThat(reloaded.getDailyVtonCount()).isEqualTo(0);

        VtonJob job = vtonJobRepository.findAll().stream()
                .filter(j -> j.getUser().getId().equals(userId))
                .findFirst()
                .orElseThrow();
        assertThat(job.getStatus()).isEqualTo(VtonJobStatus.FAILED);
        assertThat(job.isQuotaRefunded()).isTrue();
    }

    @Test
    void failedWorkerStatusRefundsQuotaOnce() {
        User user = new User("vton-fail-st", "vton-fail-st@aura.app");
        user.addWardrobeItem(new WardrobeItem("skirt", 0.9, PNG, "image/png", "red"));
        user = userRepository.save(user);
        Long itemId = user.getWardrobeItems().getFirst().getId();

        when(vtonWorkerClient.enqueue(any())).thenReturn("worker-fail-1");
        when(vtonWorkerClient.status("worker-fail-1"))
                .thenReturn(new VtonWorkerClient.WorkerStatusSnapshot(
                        "FAILED", null, null, "inference boom"));

        var created = vtonService.request(user.getId(), new VtonRequest(itemId, null, PNG, null));
        User afterConsume = userRepository.findById(user.getId()).orElseThrow();
        assertThat(afterConsume.getDailyVtonCount()).isEqualTo(1);

        vtonService.status(created.jobId(), user.getId());
        User afterFail = userRepository.findById(user.getId()).orElseThrow();
        assertThat(afterFail.getDailyVtonCount()).isEqualTo(0);

        // Ikinci poll cift iade yapmaz
        vtonService.status(created.jobId(), user.getId());
        User afterSecond = userRepository.findById(user.getId()).orElseThrow();
        assertThat(afterSecond.getDailyVtonCount()).isEqualTo(0);
    }
}
