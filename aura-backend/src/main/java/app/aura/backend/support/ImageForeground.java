package app.aura.backend.support;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * Dolap / VTON görseli: header'dan çözünürlük (decode yok) + isteğe bağlı boş-tuval.
 *
 * Ürün kararı: 48MP (8000×6000) bilinçli reddedilir — Vision'da 24MP JPEG
 * ~4.7GB tepe RAM (denetçi). Java MAX_PIXELS = Python image_limits.MAX_PIXELS.
 *
 * 12 baytlık test sahte PNG (&lt;24) atlanır. Parse edilemeyen ≥24 bayt
 * fail-closed (DECODE_FAILED); ImageIO'suz WebP/BMP magic ile okunur.
 *
 * inspect() içindeki OutOfMemoryError yakalaması savunma ağıdır: header
 * limiti + 512px altörnekleme sonrası pratikte tetiklenmez (ölü kod yolu).
 */
public final class ImageForeground {

    /** ~24 MP. aura-vision/app/services/image_limits.py ile senkron. */
    public static final long MAX_PIXELS = 24_000_000L;

    public static final int MAX_SIDE = 8192;

    /** Test sahte PNG'leri (12 bayt) bu eşiğin altında; üretim yükü değil. */
    public static final int MIN_PARSE_BYTES = 24;

    private static final int MIN_DISTINCT_PX = 200;
    private static final int COLOR_DELTA = 12;
    private static final int BLANK_MAX_SIDE = 512;

    public enum Verdict {
        OK,
        BLANK,
        TOO_LARGE,
        DECODE_FAILED
    }

    public record Inspection(Verdict verdict, int width, int height) {
        public static Inspection ok() {
            return new Inspection(Verdict.OK, 0, 0);
        }
    }

    private ImageForeground() {}

    /** Yalnız IHDR/SOF/VP8/BMP — piksel decode yok. Parse yoksa fail-closed. */
    public static Inspection inspectHeader(byte[] bytes) {
        if (bytes == null || bytes.length < MIN_PARSE_BYTES) {
            return Inspection.ok();
        }
        int[] size = peekWidthHeight(bytes);
        if (size == null) {
            return new Inspection(Verdict.DECODE_FAILED, 0, 0);
        }
        int width = size[0];
        int height = size[1];
        if (width <= 0 || height <= 0) {
            return new Inspection(Verdict.DECODE_FAILED, width, height);
        }
        if (exceedsLimit(width, height)) {
            return new Inspection(Verdict.TOO_LARGE, width, height);
        }
        return new Inspection(Verdict.OK, width, height);
    }

    /**
     * Header sınırı + (gerekirse) altörneklenmiş decode.
     *
     * {@code OutOfMemoryError} yakalanır ve DECODE_FAILED döner; ancak header
     * MAX_PIXELS/MAX_SIDE kestikten sonra okuma en fazla ~512px kenara
     * altörneklenir. -Xmx24m'de bile yasal görseller bu yola girmez — bu catch
     * pratikte ölü kod / savunma ağıdır, canlı OOM kanıtı değildir.
     */
    public static Inspection inspect(byte[] bytes) {
        Inspection header = inspectHeader(bytes);
        if (header.verdict() != Verdict.OK) {
            return header;
        }
        if (bytes == null || bytes.length < MIN_PARSE_BYTES) {
            return Inspection.ok();
        }
        BufferedImage image;
        try {
            image = readSubsampled(bytes, header.width(), header.height());
        } catch (OutOfMemoryError | NegativeArraySizeException ex) {
            return verdictForDecodeFailure(ex, header.width(), header.height());
        } catch (IOException | RuntimeException ex) {
            return new Inspection(Verdict.DECODE_FAILED, header.width(), header.height());
        }
        if (image == null) {
            return Inspection.ok();
        }
        int width = image.getWidth();
        int height = image.getHeight();
        if (width <= 0 || height <= 0) {
            return Inspection.ok();
        }
        if (isUniformCanvas(image)) {
            return new Inspection(Verdict.BLANK, width, height);
        }
        return new Inspection(Verdict.OK, width, height);
    }

    public static boolean isBlankCanvas(byte[] bytes) {
        return inspect(bytes).verdict() == Verdict.BLANK;
    }

    static Inspection verdictForDecodeFailure(Throwable error) {
        return verdictForDecodeFailure(error, 0, 0);
    }

    static Inspection verdictForDecodeFailure(Throwable error, int width, int height) {
        if (error instanceof OutOfMemoryError || error instanceof NegativeArraySizeException) {
            return new Inspection(Verdict.DECODE_FAILED, width, height);
        }
        return Inspection.ok();
    }

    public static boolean exceedsLimit(int width, int height) {
        if (width > MAX_SIDE || height > MAX_SIDE) {
            return true;
        }
        return (long) width * (long) height > MAX_PIXELS;
    }

    static int[] peekWidthHeight(byte[] bytes) {
        int[] fromReader = peekViaImageIo(bytes);
        if (fromReader != null) {
            return fromReader;
        }
        int[] fromMagic = peekViaMagic(bytes);
        if (fromMagic != null) {
            return fromMagic;
        }
        return null;
    }

    private static int[] peekViaImageIo(byte[] bytes) {
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (stream == null) {
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                return new int[] {reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }

    /**
     * ImageIO eklentisi olmayan WebP ve klasik BMP.
     * Parse edilemezse null — çağıran fail-closed.
     */
    static int[] peekViaMagic(byte[] bytes) {
        if (bytes.length >= 12
                && bytes[0] == 'R'
                && bytes[1] == 'I'
                && bytes[2] == 'F'
                && bytes[3] == 'F'
                && bytes[8] == 'W'
                && bytes[9] == 'E'
                && bytes[10] == 'B'
                && bytes[11] == 'P') {
            return peekWebP(bytes);
        }
        if (bytes.length >= 26 && bytes[0] == 'B' && bytes[1] == 'M') {
            return peekBmp(bytes);
        }
        return null;
    }

    private static int[] peekWebP(byte[] bytes) {
        int offset = 12;
        while (offset + 8 <= bytes.length) {
            int chunkType0 = bytes[offset] & 0xff;
            int chunkType1 = bytes[offset + 1] & 0xff;
            int chunkType2 = bytes[offset + 2] & 0xff;
            int chunkType3 = bytes[offset + 3] & 0xff;
            int chunkSize = u32le(bytes, offset + 4);
            if (chunkSize < 0) {
                return null;
            }
            int payload = offset + 8;
            if (chunkType0 == 'V' && chunkType1 == 'P' && chunkType2 == '8' && chunkType3 == 'X') {
                if (payload + 10 > bytes.length) {
                    return null;
                }
                int width = 1 + u24le(bytes, payload + 4);
                int height = 1 + u24le(bytes, payload + 7);
                return new int[] {width, height};
            }
            if (chunkType0 == 'V' && chunkType1 == 'P' && chunkType2 == '8' && chunkType3 == ' ') {
                return peekVp8(bytes, payload);
            }
            if (chunkType0 == 'V' && chunkType1 == 'P' && chunkType2 == '8' && chunkType3 == 'L') {
                return peekVp8L(bytes, payload);
            }
            long next = (long) payload + chunkSize + (chunkSize & 1);
            if (next <= offset || next > bytes.length) {
                return null;
            }
            offset = (int) next;
        }
        return null;
    }

    private static int[] peekVp8(byte[] bytes, int payload) {
        if (payload + 10 > bytes.length) {
            return null;
        }
        if ((bytes[payload + 3] & 0xff) != 0x9d
                || (bytes[payload + 4] & 0xff) != 0x01
                || (bytes[payload + 5] & 0xff) != 0x2a) {
            return null;
        }
        int width = (bytes[payload + 6] & 0xff) | ((bytes[payload + 7] & 0x3f) << 8);
        int height = (bytes[payload + 8] & 0xff) | ((bytes[payload + 9] & 0x3f) << 8);
        return new int[] {width, height};
    }

    private static int[] peekVp8L(byte[] bytes, int payload) {
        if (payload + 5 > bytes.length) {
            return null;
        }
        if ((bytes[payload] & 0xff) != 0x2f) {
            return null;
        }
        int bits = (bytes[payload + 1] & 0xff)
                | ((bytes[payload + 2] & 0xff) << 8)
                | ((bytes[payload + 3] & 0xff) << 16)
                | ((bytes[payload + 4] & 0xff) << 24);
        int width = (bits & 0x3fff) + 1;
        int height = ((bits >> 14) & 0x3fff) + 1;
        return new int[] {width, height};
    }

    private static int[] peekBmp(byte[] bytes) {
        int width = i32le(bytes, 18);
        int height = Math.abs(i32le(bytes, 22));
        if (width <= 0 || height <= 0) {
            return null;
        }
        return new int[] {width, height};
    }

    private static int u24le(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff)
                | ((bytes[offset + 1] & 0xff) << 8)
                | ((bytes[offset + 2] & 0xff) << 16);
    }

    private static int u32le(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff)
                | ((bytes[offset + 1] & 0xff) << 8)
                | ((bytes[offset + 2] & 0xff) << 16)
                | ((bytes[offset + 3] & 0xff) << 24);
    }

    private static int i32le(byte[] bytes, int offset) {
        return u32le(bytes, offset);
    }

    private static BufferedImage readSubsampled(byte[] bytes, int headerW, int headerH)
            throws IOException {
        int srcW = headerW > 0 ? headerW : 1;
        int srcH = headerH > 0 ? headerH : 1;
        int step = Math.max(1, Math.max(srcW, srcH) / BLANK_MAX_SIDE);
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (stream == null) {
                return ImageIO.read(new ByteArrayInputStream(bytes));
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                return ImageIO.read(new ByteArrayInputStream(bytes));
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                ImageReadParam param = reader.getDefaultReadParam();
                if (step > 1) {
                    param.setSourceSubsampling(step, step, 0, 0);
                }
                return reader.read(0, param);
            } finally {
                reader.dispose();
            }
        }
    }

    private static boolean isUniformCanvas(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int ref = image.getRGB(0, 0);
        int distinct = 0;
        int step = Math.max(1, (width * height) / 20_000);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x += step) {
                int rgb = image.getRGB(x, y);
                if (channelDelta(rgb, ref) > COLOR_DELTA) {
                    distinct++;
                    if (distinct >= MIN_DISTINCT_PX) {
                        return false;
                    }
                }
            }
        }
        return distinct < MIN_DISTINCT_PX;
    }

    private static int channelDelta(int a, int b) {
        int dr = ((a >> 16) & 0xff) - ((b >> 16) & 0xff);
        int dg = ((a >> 8) & 0xff) - ((b >> 8) & 0xff);
        int db = (a & 0xff) - (b & 0xff);
        return Math.abs(dr) + Math.abs(dg) + Math.abs(db);
    }
}
