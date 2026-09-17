package app.aura.backend.dto;

/** Presigned PUT + kalici object URL. */
public record PresignUploadResponse(
        String uploadUrl,
        String objectUrl,
        String bucket,
        String objectKey,
        long expiresInSeconds) {
}
