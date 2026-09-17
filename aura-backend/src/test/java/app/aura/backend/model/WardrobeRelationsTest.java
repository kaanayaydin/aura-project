package app.aura.backend.model;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * User -> WardrobeItem / UserPerfume @OneToMany iliskilerinin gercekten kalici
 * oldugunu ve cascade davranisini dogrular.
 */
@DataJpaTest
@ActiveProfiles("test")
class WardrobeRelationsTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    void userSavesWardrobeItemsAndPerfumesThroughCascade() {
        User user = new User("ilyaskaan", "ilyaskaan@aura.app");
        user.addWardrobeItem(new WardrobeItem("t-shirt", "aGVsbG8=", "white"));
        user.addWardrobeItem(new WardrobeItem("jacket", "aGVsbG8y", "navy"));
        user.addPerfume(new UserPerfume(
                "adp-colonia",
                "Acqua di Parma",
                "Colonia",
                "EDC",
                "citrus, fresh",
                "ust: bergamot | dip: vetiver",
                "light"));

        entityManager.persist(user);
        entityManager.flush();
        entityManager.clear();

        User reloaded = entityManager.find(User.class, user.getId());

        assertThat(reloaded.getUsername()).isEqualTo("ilyaskaan");
        assertThat(reloaded.getWardrobeItems()).hasSize(2);
        assertThat(reloaded.getPerfumes()).hasSize(1);
        assertThat(reloaded.getWardrobeItems())
                .extracting(WardrobeItem::getCategory)
                .containsExactlyInAnyOrder("t-shirt", "jacket");
        assertThat(reloaded.getPerfumes().get(0).getUser().getId()).isEqualTo(reloaded.getId());
        assertThat(reloaded.getPerfumes().get(0).getCatalogId()).isEqualTo("adp-colonia");
    }

    @Test
    void removingItemFromUserDeletesItThroughOrphanRemoval() {
        User user = new User("testuser", "test@aura.app");
        WardrobeItem item = new WardrobeItem("sneakers", "aGVsbG8z", "black");
        user.addWardrobeItem(item);
        entityManager.persist(user);
        entityManager.flush();

        Long itemId = item.getId();
        user.removeWardrobeItem(item);
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(WardrobeItem.class, itemId)).isNull();
    }
}
