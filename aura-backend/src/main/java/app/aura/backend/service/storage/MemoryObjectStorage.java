package app.aura.backend.service.storage;

import app.aura.backend.config.StorageProperties;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Test / offline: bellek ici object storage.
 * Presigned URL gercek HTTP PUT degildir; {@link #putObject} ile dogrudan yazilir.
 */
@Component
@ConditionalOnProperty(
        prefix = "aura.storage",
        name = "provider",
        havingValue = "memory",
        matchIfMissing = true)
public class MemoryObjectStorage implements ObjectStorage {

    private final StorageProperties properties;
    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

    public MemoryObjectStorage(StorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public PresignedUpload generatePresignedUploadUrl(
            String bucket, String key, String contentType, Duration ttl) {
        String objectUrl = generatePublicUrl(bucket, key);
        // Test istemcileri dogrudan putObject kullanir; uploadUrl ayni objectUrl'i gosterir.
        return new PresignedUpload(objectUrl + "?presign=1", objectUrl, bucket, key, ttl);
    }

    @Override
    public String generatePublicUrl(String bucket, String key) {
        String base = properties.publicBaseUrl().replaceAll("/$", "");
        return base + "/memory/" + bucket + "/" + key;
    }

    @Override
    public void putObject(String bucket, String key, byte[] bytes, String contentType) {
        objects.put(bucket + "/" + key, bytes == null ? new byte[0] : bytes.clone());
    }

    @Override
    public void deleteObject(String bucket, String key) {
        objects.remove(bucket + "/" + key);
    }

    public byte[] getObject(String bucket, String key) {
        return objects.get(bucket + "/" + key);
    }
}
