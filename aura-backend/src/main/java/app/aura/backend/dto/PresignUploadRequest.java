package app.aura.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Presigned upload URL istegi. */
public record PresignUploadRequest(
        @NotBlank @Size(max = 40) String purpose,
        @NotBlank @Size(max = 100) String contentType,
        @Size(max = 200) String filename) {
}
