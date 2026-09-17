package app.aura.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.aura.backend.config.StorageProperties;
import app.aura.backend.service.StorageService.Purpose;
import app.aura.backend.service.storage.MemoryObjectStorage;
import app.aura.backend.service.storage.ObjectStorage.PresignedUpload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StorageServiceTest {

    private StorageService storageService;
    private MemoryObjectStorage memory;

    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties(
                "memory",
                "http://127.0.0.1:9000",
                "http://cdn.test",
                "us-east-1",
                "k",
                "s",
                true,
                600,
                "aura-wardrobe",
                "aura-vton",
                "aura-avatars");
        memory = new MemoryObjectStorage(properties);
        storageService = new StorageService(memory, properties);
    }

    @Test
    void generatesPresignedUploadForWardrobe() {
        PresignedUpload upload = storageService.generatePresignedUploadUrl(
                Purpose.WARDROBE, "image/png", "shirt.png");
        assertThat(upload.bucket()).isEqualTo("aura-wardrobe");
        assertThat(upload.key()).startsWith("items/");
        assertThat(upload.objectUrl()).contains("aura-wardrobe");
        assertThat(upload.ttl().toSeconds()).isEqualTo(600);
    }

    @Test
    void uploadBytesStoresAndReturnsPublicUrl() {
        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47};
        String url = storageService.uploadBytes(Purpose.VTON_RESULT, png, "image/png", "job-1");
        assertThat(url).startsWith("http://cdn.test/memory/aura-vton/");
        String key = url.substring(url.indexOf("aura-vton/") + "aura-vton/".length());
        assertThat(memory.getObject("aura-vton", key)).containsExactly(png);
    }
}
