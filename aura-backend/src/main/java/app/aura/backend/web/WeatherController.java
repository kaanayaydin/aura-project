package app.aura.backend.web;

import app.aura.backend.dto.WeatherSnapshot;
import app.aura.backend.service.WeatherService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Anlik hava durumu HTTP arayuzu.
 */
@RestController
@RequestMapping("/api/v1/weather")
public class WeatherController {

    private final WeatherService weatherService;

    public WeatherController(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @GetMapping("/current")
    public WeatherSnapshot current(
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lon) {
        return weatherService.current(lat, lon);
    }
}
