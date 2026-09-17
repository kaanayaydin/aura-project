package app.aura.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WardrobeServiceUrlRewriteTest {

    @Test
    void rewritesDockerMinioHostsToLoopback() {
        assertThat(WardrobeService.rewriteLocalMinioHost("http://minio:9000/aura-wardrobe/a.png"))
                .isEqualTo("http://127.0.0.1:9000/aura-wardrobe/a.png");
        assertThat(WardrobeService.rewriteLocalMinioHost("http://aura-minio:9000/b.png"))
                .isEqualTo("http://127.0.0.1:9000/b.png");
        assertThat(WardrobeService.rewriteLocalMinioHost("http://localhost:9000/c.png"))
                .isEqualTo("http://127.0.0.1:9000/c.png");
        assertThat(WardrobeService.rewriteLocalMinioHost("http://127.0.0.1:9000/d.png"))
                .isEqualTo("http://127.0.0.1:9000/d.png");
    }
}
