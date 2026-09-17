package app.aura.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.aura.backend.config.WeatherProperties;
import app.aura.backend.dto.WeatherSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class WeatherServiceTest {

    @Test
    void simulateProducesSeasonalSnapshot() {
        WeatherProperties properties = new WeatherProperties(
                41.0082, 28.9784, "Istanbul", false);
        WeatherService service = new WeatherService(properties, RestClient.builder());

        WeatherSnapshot snapshot = service.current(null, null);

        assertThat(snapshot.source()).isEqualTo("simulated");
        assertThat(snapshot.locationName()).isEqualTo("Istanbul");
        assertThat(snapshot.temperatureCelsius()).isBetween(-10.0, 45.0);
        assertThat(snapshot.humidityPercent()).isBetween(0.0, 100.0);
        assertThat(snapshot.condition()).isNotBlank();
    }

    @Test
    void conditionLabelMapsClearCode() {
        assertThat(WeatherService.conditionLabel(0)).isEqualTo("Clear");
        assertThat(WeatherService.conditionLabel(61)).isEqualTo("Rain");
    }
}
