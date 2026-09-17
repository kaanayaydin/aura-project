package app.aura.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * S3 uyumlu object storage (MinIO / Cloudflare R2).
 *
 * @param provider     s3 | memory (test)
 * @param endpoint     ornek: http://127.0.0.1:9000
 * @param publicBaseUrl CDN veya MinIO public okuma (bos ise endpoint)
 * @param region       R2 icin "auto"
 * @param pathStyle    MinIO icin true; AWS icin false
 */
@ConfigurationProperties(prefix = "aura.storage")
public record StorageProperties(
        String provider,
        String endpoint,
        String publicBaseUrl,
        String region,
        String accessKey,
        String secretKey,
        boolean pathStyle,
        int uploadTtlSeconds,
        String wardrobeBucket,
        String vtonBucket,
        String avatarsBucket) {

    public StorageProperties {
        if (provider == null || provider.isBlank()) {
            provider = "memory";
        }
        if (endpoint == null || endpoint.isBlank()) {
            endpoint = "http://127.0.0.1:9000";
        }
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            publicBaseUrl = endpoint;
        }
        if (region == null || region.isBlank()) {
            region = "us-east-1";
        }
        if (accessKey == null || accessKey.isBlank()) {
            accessKey = "aura_minio";
        }
        if (secretKey == null || secretKey.isBlank()) {
            secretKey = "aura_minio_secret";
        }
        if (uploadTtlSeconds <= 0) {
            uploadTtlSeconds = 900;
        }
        if (wardrobeBucket == null || wardrobeBucket.isBlank()) {
            wardrobeBucket = "aura-wardrobe";
        }
        if (vtonBucket == null || vtonBucket.isBlank()) {
            vtonBucket = "aura-vton";
        }
        if (avatarsBucket == null || avatarsBucket.isBlank()) {
            avatarsBucket = "aura-avatars";
        }
    }

    public boolean isS3() {
        return "s3".equalsIgnoreCase(provider);
    }
}
