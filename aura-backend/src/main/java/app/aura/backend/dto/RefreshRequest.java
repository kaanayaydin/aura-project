package app.aura.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Refresh token rotasyon istegi.
 */
public record RefreshRequest(
        @NotBlank(message = "refreshToken zorunludur")
        @Size(max = 512)
        String refreshToken) {
}
