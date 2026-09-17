package app.aura.backend.engine;

import static org.assertj.core.api.Assertions.assertThat;

import app.aura.backend.model.User;
import app.aura.backend.model.WardrobeItem;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Kural motorunun sicaklik / etkinlik tercihlerini dogrular (Spring yok).
 */
class OutfitRuleEngineTest {

    private final OutfitRuleEngine engine = new OutfitRuleEngine();
    private User owner;

    @BeforeEach
    void setUp() {
        owner = new User("engine-test", "engine@aura.local");
    }

    @Test
    void prefersJacketAndShirtForColdMeeting() {
        WardrobeItem jacket = item("jacket", 0.9);
        WardrobeItem tshirt = item("t-shirt", 0.95);
        WardrobeItem shirt = item("shirt", 0.85);
        WardrobeItem pants = item("pants", 0.9);
        WardrobeItem sneakers = item("sneakers", 0.8);
        WardrobeItem watch = item("watch", 0.7);

        OutfitRuleEngine.OutfitPlan plan = engine.suggest(
                List.of(jacket, tshirt, shirt, pants, sneakers, watch),
                5.0,
                40.0,
                Occasion.MEETING);

        assertThat(plan.top()).isPresent();
        assertThat(plan.top().get().item().getCategory()).isIn("jacket", "shirt");
        assertThat(plan.bottom()).isPresent();
        assertThat(plan.bottom().get().item().getCategory()).isEqualTo("pants");
        assertThat(plan.accessory()).isPresent();
        assertThat(plan.accessory().get().item().getCategory()).isEqualTo("watch");
        assertThat(plan.seasonBand()).isEqualTo(SeasonBand.COLD);
        assertThat(plan.vibe()).contains("Resmi");
        assertThat(plan.matchScore()).isGreaterThan(0.5);
    }

    @Test
    void prefersTshirtAndSneakersForHotSport() {
        OutfitRuleEngine.OutfitPlan plan = engine.suggest(
                List.of(
                        item("jacket", 0.9),
                        item("t-shirt", 0.8),
                        item("shirt", 0.85),
                        item("pants", 0.9),
                        item("sneakers", 0.9),
                        item("watch", 0.7)),
                30.0,
                75.0,
                Occasion.SPORT);

        assertThat(plan.top().get().item().getCategory()).isEqualTo("t-shirt");
        assertThat(plan.accessory().get().item().getCategory()).isEqualTo("sneakers");
        assertThat(plan.seasonBand()).isEqualTo(SeasonBand.HOT);
        assertThat(plan.vibe()).containsIgnoringCase("Aktif");
    }

    @Test
    void dressCoversBottomWithNote() {
        OutfitRuleEngine.OutfitPlan plan = engine.suggest(
                List.of(item("dress", 0.9), item("glasses", 0.6)),
                22.0,
                null,
                Occasion.CASUAL);

        assertThat(plan.top().get().item().getCategory()).isEqualTo("dress");
        assertThat(plan.bottom()).isEmpty();
        assertThat(plan.notes()).anyMatch(n -> n.toLowerCase().contains("elbise"));
    }

    @Test
    void ignoresPerfumeBottle() {
        OutfitRuleEngine.OutfitPlan plan = engine.suggest(
                List.of(item("perfume bottle", 0.99), item("t-shirt", 0.7), item("pants", 0.7)),
                20.0,
                50.0,
                Occasion.CASUAL);

        assertThat(plan.top().get().item().getCategory()).isEqualTo("t-shirt");
        assertThat(plan.bottom().get().item().getCategory()).isEqualTo("pants");
        assertThat(plan.accessory()).isEmpty();
    }

    private WardrobeItem item(String category, double confidence) {
        WardrobeItem wardrobeItem = new WardrobeItem(
                category, confidence, "ZmFrZQ==", "image/png", null);
        owner.addWardrobeItem(wardrobeItem);
        return wardrobeItem;
    }
}
