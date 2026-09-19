package app.aura.backend.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dolaba yeni parca ekleme istegi.
 *
 * v0.20+: {@code imageUrl} (presigned upload sonrasi) tercih edilir.
 * {@code imageBase64} geriye uyumluluk / sunucu tarafi migrate icin opsiyonel.
 */
public record CreateWardrobeItemRequest(
        Long userId,

        @NotBlank(message = "category zorunludur")
        @Size(max = 60, message = "category en fazla 60 karakter olabilir")
        String category,

        @DecimalMin(value = "0.0", message = "categoryConfidence 0 ile 1 arasinda olmali")
        @DecimalMax(value = "1.0", message = "categoryConfidence 0 ile 1 arasinda olmali")
        Double categoryConfidence,

        String imageBase64,

        @Size(max = 1024, message = "imageUrl en fazla 1024 karakter olabilir")
        String imageUrl,

        @Size(max = 40, message = "color en fazla 40 karakter olabilir")
        String color,

        /**
         * true: istemci stüdyo/onay rotasyonunu zaten uyguladi; Vision
         * skip_orientation (yalniz 3:4 framing) ile cagrilir. JSON yoksa false.
         */
        boolean alreadyNormalized) {

    public boolean hasImageUrl() {
        return imageUrl != null && !imageUrl.isBlank();
    }

    public boolean hasImageBase64() {
        return imageBase64 != null && !imageBase64.isBlank();
    }
}
