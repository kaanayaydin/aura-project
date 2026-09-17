package app.aura.backend.service.storage;

import java.time.Duration;

/** S3 uyumlu object storage sozlesmesi (MinIO / R2 / bellek). */
public interface ObjectStorage {

    record PresignedUpload(String uploadUrl, String objectUrl, String bucket, String key, Duration ttl) {
    }

    PresignedUpload generatePresignedUploadUrl(
            String bucket, String key, String contentType, Duration ttl);

    String generatePublicUrl(String bucket, String key);

    void putObject(String bucket, String key, byte[] bytes, String contentType);

    void deleteObject(String bucket, String key);
}
