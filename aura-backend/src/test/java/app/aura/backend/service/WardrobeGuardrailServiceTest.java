package app.aura.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.aura.backend.model.User;
import app.aura.backend.model.WardrobeItem;
import app.aura.backend.repository.UserRepository;
import app.aura.backend.repository.WardrobeItemRepository;
import app.aura.backend.web.VtonOwnershipException;
import app.aura.backend.web.WardrobeItemNotFoundException;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class WardrobeGuardrailServiceTest {

    private static final String PNG = Base64.getEncoder().encodeToString(new byte[] {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A
    });

    @Autowired
    private WardrobeGuardrailService wardrobeGuardrailService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WardrobeItemRepository wardrobeItemRepository;

    @Test
    void requireOwnedItemReturnsItemForOwner() {
        User owner = userRepository.save(new User("vton-owner", "vton-owner@aura.app"));
        WardrobeItem item = new WardrobeItem("shirt", 0.9, PNG, "image/png", "navy");
        owner.addWardrobeItem(item);
        owner = userRepository.save(owner);
        Long itemId = owner.getWardrobeItems().getFirst().getId();

        WardrobeItem found = wardrobeGuardrailService.requireOwnedItem(owner.getId(), itemId);
        assertThat(found.getId()).isEqualTo(itemId);
        assertThat(found.getCategory()).isEqualTo("shirt");
    }

    @Test
    void requireOwnedItemRejectsForeignItem() {
        User owner = userRepository.save(new User("vton-a", "vton-a@aura.app"));
        User other = userRepository.save(new User("vton-b", "vton-b@aura.app"));
        WardrobeItem item = new WardrobeItem("pants", 0.8, PNG, "image/png", "black");
        owner.addWardrobeItem(item);
        owner = userRepository.save(owner);
        Long itemId = owner.getWardrobeItems().getFirst().getId();

        assertThatThrownBy(() -> wardrobeGuardrailService.requireOwnedItem(other.getId(), itemId))
                .isInstanceOf(VtonOwnershipException.class)
                .hasMessageContaining(itemId.toString());
    }

    @Test
    void requireOwnedItemRejectsMissingItem() {
        User user = userRepository.save(new User("vton-miss", "vton-miss@aura.app"));
        assertThatThrownBy(() -> wardrobeGuardrailService.requireOwnedItem(user.getId(), 999_999L))
                .isInstanceOf(WardrobeItemNotFoundException.class);
    }
}
