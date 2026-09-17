package app.aura.backend.service.storage;

import app.aura.backend.config.StorageProperties;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Component
@ConditionalOnProperty(prefix = "aura.storage", name = "provider", havingValue = "s3")
public class S3ObjectStorage implements ObjectStorage {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final StorageProperties properties;

    public S3ObjectStorage(S3Client s3Client, S3Presigner s3Presigner, StorageProperties properties) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.properties = properties;
    }

    @Override
    public PresignedUpload generatePresignedUploadUrl(
            String bucket, String key, String contentType, Duration ttl) {
        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .putObjectRequest(objectRequest)
                .build();
        String uploadUrl = s3Presigner.presignPutObject(presignRequest).url().toString();
        return new PresignedUpload(uploadUrl, generatePublicUrl(bucket, key), bucket, key, ttl);
    }

    @Override
    public String generatePublicUrl(String bucket, String key) {
        String base = properties.publicBaseUrl().replaceAll("/$", "");
        if (properties.pathStyle()) {
            return base + "/" + bucket + "/" + key;
        }
        // virtual-hosted style (R2/CDN)
        return base + "/" + key;
    }

    @Override
    public void putObject(String bucket, String key, byte[] bytes, String contentType) {
        String mime = (contentType == null || contentType.isBlank()) ? "application/octet-stream" : contentType;
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(mime)
                        .contentLength((long) bytes.length)
                        .build(),
                RequestBody.fromBytes(bytes));
    }

    @Override
    public void deleteObject(String bucket, String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }
}
