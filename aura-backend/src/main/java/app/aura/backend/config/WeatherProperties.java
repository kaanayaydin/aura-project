package app.aura.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Hava durumu varsayilanlari (konum verilmezse).
 */
@ConfigurationProperties(prefix = "aura.weather")
public record WeatherProperties(
        double defaultLatitude,
        double defaultLongitude,
        String defaultLocationName,
        boolean preferLive) {

    public WeatherProperties {
        if (defaultLatitude == 0 && defaultLongitude == 0) {
            defaultLatitude = 41.0082;
            defaultLongitude = 28.9784;
        }
        if (defaultLocationName == null || defaultLocationName.isBlank()) {
            defaultLocationName = "Istanbul";
        }
    }
}
