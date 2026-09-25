package app.aura.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * S3 uyumlu object storage (MinIO / Cloudflare R2).
 *
 * @param provider     s3 | memory (test)
 * @param endpoint     ornek: http://127.0.0.1:9000
 * @param publicBaseUrl CDN veya MinIO public okuma (bos ise endpoint)
 * @param region       R2 icin "auto"
 * @param pathStyle    MinIO icin true; R2 S3 API icin true
 * @param wardrobePublicHost R2 public development URL origin (bucket basina, path'te bucket yok)
 * @param vtonPublicHost    aura-vton icin ayni
 * @param avatarsPublicHost aura-avatars icin ayni
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
        String avatarsBucket,
        String wardrobePublicHost,
        String vtonPublicHost,
        String avatarsPublicHost) {

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
        wardrobePublicHost = blankToNull(wardrobePublicHost);
        vtonPublicHost = blankToNull(vtonPublicHost);
        avatarsPublicHost = blankToNull(avatarsPublicHost);
    }

    /**
     * R2 public host varsa {@code https://pub-….r2.dev/<key>}.
     * Yoksa path-style {@code publicBaseUrl/bucket/key} (MinIO).
     */
    public String publicObjectUrl(String bucket, String key) {
        String trimmedKey = key == null ? "" : key.replaceAll("^/+", "");
        String host = publicHostFor(bucket);
        if (host != null) {
            return stripSlash(host) + "/" + trimmedKey;
        }
        String base = stripSlash(publicBaseUrl);
        if (pathStyle) {
            return base + "/" + bucket + "/" + trimmedKey;
        }
        return base + "/" + trimmedKey;
    }

    public String publicHostFor(String bucket) {
        if (bucket == null) {
            return null;
        }
        if (bucket.equals(wardrobeBucket)) {
            return wardrobePublicHost;
        }
        if (bucket.equals(vtonBucket)) {
            return vtonPublicHost;
        }
        if (bucket.equals(avatarsBucket)) {
            return avatarsPublicHost;
        }
        return null;
    }

    public boolean isPublicReadHost(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        String needle = host.toLowerCase(java.util.Locale.ROOT);
        return hostOf(wardrobePublicHost).equals(needle)
                || hostOf(vtonPublicHost).equals(needle)
                || hostOf(avatarsPublicHost).equals(needle);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String stripSlash(String value) {
        return value.replaceAll("/$", "");
    }

    private static String hostOf(String raw) {
        if (raw == null) {
            return "";
        }
        try {
            java.net.URI uri = java.net.URI.create(raw.trim());
            String host = uri.getHost();
            return host == null ? "" : host.toLowerCase(java.util.Locale.ROOT);
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }

    public boolean isS3() {
        return "s3".equalsIgnoreCase(provider);
    }
}
