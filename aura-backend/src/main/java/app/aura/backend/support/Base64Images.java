package app.aura.backend.support;

import java.util.Base64;

/**
 * Base64 gorsel yuklerini mobil istemcilerin dogrudan kullanabilecegi standart
 * bicime getirir.
 *
 * Istemciler base64'u iki farkli sekilde gonderebiliyor: ciplak base64 veya
 * `data:image/png;base64,...` seklinde data URI. Depolamada her zaman ciplak
 * ve satir sonu icermeyen bicim tutulur; Flutter tarafinda
 * `Image.memory(base64Decode(imageBase64))` bu bicimle calisir.
 */
public final class Base64Images {

    private static final String DATA_URI_PREFIX = "data:";
    private static final String BASE64_MARKER = ";base64,";

    public static final String MIME_PNG = "image/png";
    public static final String MIME_JPEG = "image/jpeg";
    public static final String MIME_WEBP = "image/webp";
    public static final String MIME_GIF = "image/gif";
    public static final String MIME_UNKNOWN = "application/octet-stream";

    private Base64Images() {
    }

    /**
     * Data URI onekini ve satir sonlarini temizleyip ciplak base64 dondurur.
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.startsWith(DATA_URI_PREFIX)) {
            int markerIndex = value.indexOf(BASE64_MARKER);
            if (markerIndex >= 0) {
                value = value.substring(markerIndex + BASE64_MARKER.length());
            }
        }
        // MIME/base64 satir sonlari (RFC 2045) cozumlemeyi bozar
        return value.replaceAll("\\s", "");
    }

    /**
     * Gorselin ilk baytlarindan (magic bytes) MIME tipini tespit eder.
     */
    public static String detectMimeType(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return MIME_UNKNOWN;
        }
        if (startsWith(bytes, 0x89, 0x50, 0x4E, 0x47)) {
            return MIME_PNG;
        }
        if (startsWith(bytes, 0xFF, 0xD8, 0xFF)) {
            return MIME_JPEG;
        }
        if (bytes.length >= 12
                && startsWith(bytes, 0x52, 0x49, 0x46, 0x46)
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return MIME_WEBP;
        }
        if (startsWith(bytes, 0x47, 0x49, 0x46, 0x38)) {
            return MIME_GIF;
        }
        return MIME_UNKNOWN;
    }

    /**
     * Istemcinin tek satirda kullanabilecegi data URI uretir.
     */
    public static String toDataUri(String base64, String mimeType) {
        if (base64 == null) {
            return null;
        }
        String mime = (mimeType == null || mimeType.isBlank()) ? MIME_UNKNOWN : mimeType;
        return DATA_URI_PREFIX + mime + BASE64_MARKER + base64;
    }

    public static byte[] decode(String base64) {
        return Base64.getDecoder().decode(base64);
    }

    private static boolean startsWith(byte[] bytes, int... expected) {
        if (bytes.length < expected.length) {
            return false;
        }
        for (int index = 0; index < expected.length; index++) {
            if ((bytes[index] & 0xFF) != expected[index]) {
                return false;
            }
        }
        return true;
    }
}
