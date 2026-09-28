package app.aura.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Sohbet gecmisindeki tek tur.
 *
 * @param role user | assistant
 */
public record ChatTurn(
        @NotBlank @Pattern(regexp = "(?i)(user|assistant)") String role,
        @NotBlank @Size(max = 8000) String content) {
}
