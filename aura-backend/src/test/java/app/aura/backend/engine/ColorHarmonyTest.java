package app.aura.backend.engine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ColorHarmonyTest {

    @Test
    void scoresMonochromeSameTone() {
        ColorHarmony.Result result = ColorHarmony.scorePair("navy", "navy");
        assertThat(result.type()).isEqualTo(ColorHarmony.Type.MONOCHROME);
        assertThat(result.score()).isGreaterThan(0.9);
    }

    @Test
    void scoresAnalogousNeutralWithColor() {
        ColorHarmony.Result result = ColorHarmony.scorePair("black", "red");
        assertThat(result.type()).isEqualTo(ColorHarmony.Type.ANALOGOUS);
        assertThat(result.score()).isGreaterThan(0.8);
    }

    @Test
    void scoresWarmCoolContrast() {
        ColorHarmony.Result result = ColorHarmony.scorePair("red", "blue");
        assertThat(result.type()).isEqualTo(ColorHarmony.Type.CONTRAST);
        assertThat(result.score()).isBetween(0.7, 0.9);
    }

    @Test
    void outfitScoreUsesAvailableSlots() {
        ColorHarmony.Result result = ColorHarmony.scoreOutfit(
                java.util.Optional.of("white"),
                java.util.Optional.of("beige"),
                java.util.Optional.empty());
        assertThat(result.score()).isGreaterThan(0.7);
        assertThat(result.type()).isIn(
                ColorHarmony.Type.MONOCHROME,
                ColorHarmony.Type.ANALOGOUS,
                ColorHarmony.Type.NEUTRAL);
    }
}
