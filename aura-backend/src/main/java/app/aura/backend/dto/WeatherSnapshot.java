package app.aura.backend.dto;

/**
 * Anlik hava durumu ozeti.
 *
 * @param source open-meteo | simulated
 */
public record WeatherSnapshot(
        double temperatureCelsius,
        double humidityPercent,
        String condition,
        int weatherCode,
        double latitude,
        double longitude,
        String locationName,
        String source) {
}
