package app.aura.backend.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Aura oneri istegi.
 *
 * {@code userId} opsiyonel geriye uyumluluk alanidir ve yok sayilir; kimlik JWT Principal'dan gelir.
 */
public record SuggestAuraRequest(
        Long userId,

        @DecimalMin(value = "-50.0", message = "temperatureCelsius -50 ile 60 arasinda olmali")
        @DecimalMax(value = "60.0", message = "temperatureCelsius -50 ile 60 arasinda olmali")
        Double temperatureCelsius,

        @DecimalMin(value = "0.0", message = "humidityPercent 0 ile 100 arasinda olmali")
        @DecimalMax(value = "100.0", message = "humidityPercent 0 ile 100 arasinda olmali")
        Double humidityPercent,

        @NotBlank(message = "occasion zorunludur")
        @Size(max = 40, message = "occasion en fazla 40 karakter olabilir")
        String occasion,

        Boolean includeImages,

        /** true ise manuel sicaklik yok sayilir; hava servisi kullanilir. */
        Boolean useAutoWeather,

        @DecimalMin(value = "-90.0")
        @DecimalMax(value = "90.0")
        Double latitude,

        @DecimalMin(value = "-180.0")
        @DecimalMax(value = "180.0")
        Double longitude) {
}
