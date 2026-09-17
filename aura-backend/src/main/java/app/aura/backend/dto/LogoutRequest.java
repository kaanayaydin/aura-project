package app.aura.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Logout istegi — refresh token iptali.
 */
public record LogoutRequest(
        @NotBlank(message = "refreshToken zorunludur")
        @Size(max = 512)
        String refreshToken) {
}
