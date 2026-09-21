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
 * Dolap görseli: header'dan çözünürlük (decode yok) + isteğe bağlı boş-tuval.
 *
 * 12 baytlık test sahte PNG'leri atlanır. ImageIO OOM asla "geçti" dönmez.
 */
public final class ImageForeground {

    /** ~24 MP — telefon 12–16 MP geçer; 30000×30000 bombayı keser. */
    public static final long MAX_PIXELS = 24_000_000L;

    public static final int MAX_SIDE = 8192;

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

    /** Yalnız IHDR/SOF — piksel decode yok. */
    public static Inspection inspectHeader(byte[] bytes) {
        if (bytes == null || bytes.length < 24) {
            return Inspection.ok();
        }
        int[] size = peekWidthHeight(bytes);
        if (size == null) {
            return Inspection.ok();
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
     * Header sınırı + (gerekirse) altörneklenmiş decode. OOM → DECODE_FAILED.
     */
    public static Inspection inspect(byte[] bytes) {
        Inspection header = inspectHeader(bytes);
        if (header.verdict() != Verdict.OK) {
            return header;
        }
        if (bytes == null || bytes.length < 24) {
            return Inspection.ok();
        }
        BufferedImage image;
        try {
            image = readSubsampled(bytes, header.width(), header.height());
        } catch (OutOfMemoryError | NegativeArraySizeException ex) {
            return verdictForDecodeFailure(ex, header.width(), header.height());
        } catch (IOException | RuntimeException ex) {
            return Inspection.ok();
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
