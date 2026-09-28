package app.aura.backend.engine;

import static org.assertj.core.api.Assertions.assertThat;

import app.aura.backend.dto.WeatherSnapshot;
import org.junit.jupiter.api.Test;

class WeatherDisplayTest {

    @Test
    void conditionTrMatchesTheEightChatLabels() {
        assertThat(WeatherDisplay.conditionTr("Clear")).isEqualTo("Açık");
        assertThat(WeatherDisplay.conditionTr("Partly cloudy")).isEqualTo("Parçalı bulutlu");
        assertThat(WeatherDisplay.conditionTr("Fog")).isEqualTo("Sisli");
        assertThat(WeatherDisplay.conditionTr("Rain")).isEqualTo("Yağmurlu");
        assertThat(WeatherDisplay.conditionTr("Snow")).isEqualTo("Karlı");
        assertThat(WeatherDisplay.conditionTr("Showers")).isEqualTo("Sağanak yağışlı");
        assertThat(WeatherDisplay.conditionTr("Thunderstorm")).isEqualTo("Gök gürültülü fırtına");
        assertThat(WeatherDisplay.conditionTr("Unknown")).isEqualTo("Bilinmiyor");
    }

    @Test
    void summaryDropsSourceAndCoordinateLocation() {
        WeatherSnapshot live = new WeatherSnapshot(
                21.4, 60, "Partly cloudy", 2, 41.01, 28.98, "41.01, 28.98", "open-meteo");
        WeatherSnapshot named = new WeatherSnapshot(
                21.0, 60, "Clear", 0, 41.0, 29.0, "Istanbul", "simulated");

        assertThat(WeatherDisplay.summary(live)).isEqualTo("21°C, Parçalı bulutlu");
        assertThat(WeatherDisplay.summary(live)).doesNotContain("open-meteo");
        assertThat(WeatherDisplay.summary(live)).doesNotContain("Partly cloudy");
        assertThat(WeatherDisplay.summary(live)).doesNotContain("41.01");

        assertThat(WeatherDisplay.summary(named)).isEqualTo("21°C, Açık, Istanbul");
        assertThat(WeatherDisplay.summary(named)).doesNotContain("simulated");
        assertThat(WeatherDisplay.summary(named)).doesNotContain("Clear");
    }
}
