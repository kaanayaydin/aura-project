package app.aura.backend.support;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Bos stüdyo tuvali / tek renk duvar — dolaba yazılmamalı.
 *
 * Okunamayan bayt (test sahte PNG) reddedilmez; Vision 422 asıl kapıdır.
 */
public final class ImageForeground {

    private static final int MIN_DISTINCT_PX = 200;
    private static final int COLOR_DELTA = 12;

    private ImageForeground() {}

    public static boolean isBlankCanvas(byte[] bytes) {
        if (bytes == null || bytes.length < 24) {
            return false;
        }
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException ex) {
            return false;
        }
        if (image == null) {
            return false;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        if (width <= 0 || height <= 0) {
            return false;
        }
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
        int budget = Math.max(MIN_DISTINCT_PX, (width * height) / 50);
        return distinct < Math.min(MIN_DISTINCT_PX, budget);
    }

    private static int channelDelta(int a, int b) {
        int dr = ((a >> 16) & 0xff) - ((b >> 16) & 0xff);
        int dg = ((a >> 8) & 0xff) - ((b >> 8) & 0xff);
        int db = (a & 0xff) - (b & 0xff);
        return Math.abs(dr) + Math.abs(dg) + Math.abs(db);
    }
}
