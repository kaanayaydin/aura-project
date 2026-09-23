package app.aura.backend.support;

import app.aura.backend.web.InvalidImagePayloadException;
import org.springframework.http.MediaType;

/**
 * Sonuc / indirme govdesi — magic byte. Eslestirme yoksa red; varsayilan PNG yok.
 */
public final class ImageMagic {

    private static final byte[] PNG = {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    private ImageMagic() {}

    public static MediaType requireImageMediaType(byte[] bytes) {
        if (bytes == null || bytes.length < 8) {
            throw new InvalidImagePayloadException("Sonuc gorseli taninamadi");
        }
        if (startsWith(bytes, PNG)) {
            return MediaType.IMAGE_PNG;
        }
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF) {
            return MediaType.IMAGE_JPEG;
        }
        if (bytes.length >= 12
                && bytes[0] == 'R'
                && bytes[1] == 'I'
                && bytes[2] == 'F'
                && bytes[3] == 'F'
                && bytes[8] == 'W'
                && bytes[9] == 'E'
                && bytes[10] == 'B'
                && bytes[11] == 'P') {
            return MediaType.parseMediaType("image/webp");
        }
        throw new InvalidImagePayloadException("Sonuc gorseli taninamadi");
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
