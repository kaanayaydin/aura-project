package app.aura.backend.service;

import app.aura.backend.config.WeatherProperties;
import app.aura.backend.dto.WeatherSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.Month;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Dis hava durumu kaynagi (Open-Meteo) + mevsimsel simülasyon yedegi.
 *
 * API anahtari gerektirmez. Canli cagri basarisiz olursa konum/aya gore
 * akilli bir tahmini snapshot uretir; öneri akisi hicbir zaman kirilmaz.
 */
@Service
public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);
    private static final String OPEN_METEO =
            "https://api.open-meteo.com/v1/forecast"
                    + "?latitude={lat}&longitude={lon}"
                    + "&current=temperature_2m,relative_humidity_2m,weather_code"
                    + "&timezone=auto";

    private final WeatherProperties properties;
    private final RestClient restClient;

    public WeatherService(WeatherProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    public WeatherSnapshot current(Double latitude, Double longitude) {
        double lat = latitude != null ? latitude : properties.defaultLatitude();
        double lon = longitude != null ? longitude : properties.defaultLongitude();
        String location = resolveLocationName(latitude, longitude);

        if (properties.preferLive()) {
            try {
                return fetchLive(lat, lon, location);
            } catch (Exception exception) {
                log.warn("Canli hava alinamadi, simülasyona dusuluyor: {}", exception.getMessage());
            }
        }
        return simulate(lat, lon, location);
    }

    private WeatherSnapshot fetchLive(double lat, double lon, String location) {
        JsonNode root = restClient.get()
                .uri(OPEN_METEO, lat, lon)
                .retrieve()
                .body(JsonNode.class);
        if (root == null || !root.has("current")) {
            throw new IllegalStateException("Open-Meteo cevabi bos.");
        }
        JsonNode current = root.get("current");
        double temperature = current.path("temperature_2m").asDouble();
        double humidity = current.path("relative_humidity_2m").asDouble(55);
        int code = current.path("weather_code").asInt(0);
        return new WeatherSnapshot(
                round1(temperature),
                round1(humidity),
                conditionLabel(code),
                code,
                lat,
                lon,
                location,
                "open-meteo");
    }

    /**
     * Istanbul iklimine yakin basit mevsimsel model; baska konumlar icin
     * enlem ile kaba kayma uygular.
     */
    WeatherSnapshot simulate(double lat, double lon, String location) {
        Month month = LocalDate.now().getMonth();
        double baseTemp = switch (month) {
            case DECEMBER, JANUARY, FEBRUARY -> 7.0;
            case MARCH, APRIL -> 14.0;
            case MAY, JUNE -> 22.0;
            case JULY, AUGUST -> 28.0;
            case SEPTEMBER, OCTOBER -> 20.0;
            case NOVEMBER -> 12.0;
        };
        // Kuzey enlemlerde biraz daha serin
        double latitudeShift = (41.0 - lat) * 0.35;
        double temperature = baseTemp + latitudeShift;
        double humidity = switch (month) {
            case DECEMBER, JANUARY, FEBRUARY -> 75.0;
            case JUNE, JULY, AUGUST -> 55.0;
            default -> 62.0;
        };
        String condition = temperature >= 26 ? "Clear" : temperature <= 8 ? "Cloudy" : "Partly cloudy";
        int code = temperature >= 26 ? 0 : temperature <= 8 ? 3 : 2;
        return new WeatherSnapshot(
                round1(temperature),
                round1(humidity),
                condition,
                code,
                lat,
                lon,
                location,
                "simulated");
    }

    private String resolveLocationName(Double latitude, Double longitude) {
        if (latitude == null && longitude == null) {
            return properties.defaultLocationName();
        }
        return String.format(Locale.ROOT, "%.2f, %.2f",
                latitude != null ? latitude : properties.defaultLatitude(),
                longitude != null ? longitude : properties.defaultLongitude());
    }

    static String conditionLabel(int weatherCode) {
        // WMO Weather interpretation codes (Open-Meteo)
        if (weatherCode == 0) {
            return "Clear";
        }
        if (weatherCode <= 3) {
            return "Partly cloudy";
        }
        if (weatherCode <= 48) {
            return "Fog";
        }
        if (weatherCode <= 67) {
            return "Rain";
        }
        if (weatherCode <= 77) {
            return "Snow";
        }
        if (weatherCode <= 82) {
            return "Showers";
        }
        if (weatherCode <= 99) {
            return "Thunderstorm";
        }
        return "Unknown";
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
