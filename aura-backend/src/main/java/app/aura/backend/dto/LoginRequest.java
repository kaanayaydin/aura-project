package app.aura.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Email/sifre giris istegi.
 */
public record LoginRequest(
        @NotBlank(message = "email zorunludur")
        @Email(message = "email formati gecersiz")
        @Size(max = 120)
        String email,

        @NotBlank(message = "password zorunludur")
        @Size(max = 128)
        String password,

        /** Opsiyonel; refresh_tokens.device_info. */
        @Size(max = 255)
        String deviceInfo) {
}
