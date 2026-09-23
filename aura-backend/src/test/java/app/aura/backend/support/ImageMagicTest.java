package app.aura.backend.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.aura.backend.web.InvalidImagePayloadException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class ImageMagicTest {

    @Test
    void pngMagicIsPng() {
        byte[] png = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01
        };
        assertThat(ImageMagic.requireImageMediaType(png)).isEqualTo(MediaType.IMAGE_PNG);
    }

    @Test
    void jpegMagicIsJpeg() {
        byte[] jpeg = new byte[16];
        jpeg[0] = (byte) 0xFF;
        jpeg[1] = (byte) 0xD8;
        jpeg[2] = (byte) 0xFF;
        jpeg[3] = (byte) 0xE0;
        assertThat(ImageMagic.requireImageMediaType(jpeg)).isEqualTo(MediaType.IMAGE_JPEG);
    }

    @Test
    void webpMagicIsWebp() {
        byte[] webp = new byte[16];
        webp[0] = 'R';
        webp[1] = 'I';
        webp[2] = 'F';
        webp[3] = 'F';
        webp[8] = 'W';
        webp[9] = 'E';
        webp[10] = 'B';
        webp[11] = 'P';
        assertThat(ImageMagic.requireImageMediaType(webp).toString()).isEqualTo("image/webp");
    }

    @Test
    void garbageIsRejectedNotDefaultPng() {
        byte[] garbage = "not-an-image-at-all!!".getBytes();
        assertThatThrownBy(() -> ImageMagic.requireImageMediaType(garbage))
                .isInstanceOf(InvalidImagePayloadException.class)
                .hasMessageContaining("taninamadi");
    }
}
