package app.aura.backend.dto;

import app.aura.backend.validation.NotDisposableEmail;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Kayit istegi — sikı email + parola karmasikligi (v0.18.1).
 */
public record RegisterRequest(
        @NotBlank(message = "email zorunludur")
        @Email(
                regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$",
                message = "email formati gecersiz")
        @NotDisposableEmail
        @Size(max = 120)
        String email,

        @NotBlank(message = "password zorunludur")
        @Size(min = 8, max = 128, message = "password en az 8 karakter olmali")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$",
                message = "password en az 1 buyuk, 1 kucuk, 1 rakam ve 1 ozel karakter icermelidir")
        String password,

        /** Opsiyonel gorunen ad; yoksa email yerel kismi username olur. */
        @Size(max = 80)
        String displayName) {
}
