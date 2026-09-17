package app.aura.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Virtual Try-On istek govdesi.
 *
 * v0.20+: {@code personImageUrl} tercih; base64 opsiyonel/geriye uyum.
 */
public record VtonRequest(
        @NotNull Long wardrobeItemId,
        Long userId,
        @Size(max = 20_000_000) String personImageBase64,
        @Size(max = 1024) String personImageUrl) {
}
