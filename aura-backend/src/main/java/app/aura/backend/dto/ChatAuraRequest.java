package app.aura.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Aura AI sohbet istegi.
 *
 * {@code userId} opsiyonel geriye uyumluluk alanidir ve yok sayilir; kimlik JWT Principal'dan gelir.
 */
public record ChatAuraRequest(
        @NotBlank @Size(max = 4000) String message,
        List<ChatTurn> history,
        Long userId,
        Double latitude,
        Double longitude) {
}
