package app.aura.backend.engine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PerfumeRuleEngineTest {

    private final PerfumeRuleEngine engine = new PerfumeRuleEngine();

    @Test
    void prefersFreshLightForHotHumidSport() {
        PerfumeRuleEngine.PerfumePick pick = engine.recommend(
                SeasonBand.HOT,
                Occasion.SPORT,
                80.0,
                "Nefes Alabilir & Aktif");

        assertThat(pick.perfume().chords())
                .anyMatch(c -> c.equalsIgnoreCase("fresh")
                        || c.equalsIgnoreCase("aquatic")
                        || c.equalsIgnoreCase("citrus")
                        || c.equalsIgnoreCase("green"));
        assertThat(pick.perfume().diffusion()).isIn("light", "soft", "moderate");
        assertThat(pick.score()).isGreaterThan(0.5);
        assertThat(pick.thermodynamicNote()).isNotBlank();
    }

    @Test
    void prefersWoodySpicyForColdMeeting() {
        PerfumeRuleEngine.PerfumePick pick = engine.recommend(
                SeasonBand.COLD,
                Occasion.MEETING,
                40.0,
                "Sicak Tutan & Resmi");

        assertThat(pick.perfume().chords())
                .anyMatch(c -> c.equalsIgnoreCase("woody")
                        || c.equalsIgnoreCase("spicy")
                        || c.equalsIgnoreCase("oriental"));
        assertThat(pick.perfume().bestOccasions()).anyMatch(o -> o.equalsIgnoreCase("meeting"));
        assertThat(pick.score()).isGreaterThan(0.5);
    }

    @Test
    void catalogLoadsCuratedEntries() {
        assertThat(PerfumeCatalog.all()).hasSizeGreaterThanOrEqualTo(10);
        assertThat(PerfumeCatalog.all().getFirst().topNotes()).isNotEmpty();
        assertThat(PerfumeCatalog.all().getFirst().baseNotes()).isNotEmpty();
    }
}
