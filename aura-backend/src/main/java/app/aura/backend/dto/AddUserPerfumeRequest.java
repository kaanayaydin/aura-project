package app.aura.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Kullanici rafina katalogdan parfum ekleme istegi.
 *
 * {@code userId} opsiyonel geriye uyumluluk alanidir ve yok sayilir; kimlik JWT Principal'dan gelir.
 *
 * @param catalogId kuratorlu katalogdaki id (orn. adp-colonia)
 */
public record AddUserPerfumeRequest(
        Long userId,

        @NotBlank(message = "catalogId zorunludur")
        @Size(max = 80, message = "catalogId en fazla 80 karakter olabilir")
        String catalogId) {
}
