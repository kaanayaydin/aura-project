package app.aura.backend.engine;

import app.aura.backend.dto.WeatherSnapshot;
import java.util.Locale;

/**
 * Sohbet metinleri için Türkçe hava görüntüsü.
 *
 * {@code WeatherSnapshot.condition()} İngilizce kalır; öneri API'si onu okumaya devam eder.
 */
public final class WeatherDisplay {

    private WeatherDisplay() {
    }

    /** Open-Meteo / {@code conditionLabel} İngilizce değerinin sohbet karşılığı. */
    public static String conditionTr(String condition) {
        if (condition == null || condition.isBlank()) {
            return "Bilinmiyor";
        }
        return switch (condition.trim().toLowerCase(Locale.ROOT)) {
            case "clear" -> "Açık";
            case "partly cloudy" -> "Parçalı bulutlu";
            case "fog" -> "Sisli";
            case "rain" -> "Yağmurlu";
            case "snow" -> "Karlı";
            case "showers" -> "Sağanak yağışlı";
            case "thunderstorm" -> "Gök gürültülü fırtına";
            case "unknown" -> "Bilinmiyor";
            case "cloudy" -> "Bulutlu";
            default -> "Bilinmiyor";
        };
    }

    /**
     * {@code 21°C, Parçalı bulutlu} veya yer adı gerçekse {@code …, Istanbul}.
     * Kaynak adı ve koordinat biçimi eklenmez.
     */
    public static String summary(WeatherSnapshot weather) {
        String base = "%.0f°C, %s".formatted(
                weather.temperatureCelsius(),
                conditionTr(weather.condition()));
        String place = weather.locationName();
        if (place == null || place.isBlank() || isCoordinateLabel(place)) {
            return base;
        }
        return base + ", " + place.trim();
    }

    static boolean isCoordinateLabel(String location) {
        return location.trim().matches("-?\\d+\\.\\d+,\\s*-?\\d+\\.\\d+");
    }
}
