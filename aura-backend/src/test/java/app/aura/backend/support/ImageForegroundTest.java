package app.aura.backend.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ImageForegroundTest {

    @Test
    void hugeDeclaredPngRejectedFromHeaderWithoutFullDecode() {
        byte[] bomb = PngBombs.declaredSize(30_000, 30_000);
        assertThat(bomb.length).isLessThan(2_000);

        ImageForeground.Inspection header = ImageForeground.inspectHeader(bomb);
        assertThat(header.verdict()).isEqualTo(ImageForeground.Verdict.TOO_LARGE);
        assertThat(header.width()).isEqualTo(30_000);
        assertThat(header.height()).isEqualTo(30_000);

        ImageForeground.Inspection full = ImageForeground.inspect(bomb);
        assertThat(full.verdict()).isEqualTo(ImageForeground.Verdict.TOO_LARGE);
    }

    @Test
    void concurrentHugeHeadersAllRejected() throws Exception {
        byte[] bomb = PngBombs.declaredSize(30_000, 30_000);
        int n = 8;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        List<Future<ImageForeground.Verdict>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            futures.add(pool.submit(() -> ImageForeground.inspect(bomb).verdict()));
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        for (Future<ImageForeground.Verdict> future : futures) {
            assertThat(future.get()).isEqualTo(ImageForeground.Verdict.TOO_LARGE);
        }
    }

    @Test
    void outOfMemoryIsRejectNotPass() {
        ImageForeground.Inspection oom =
                ImageForeground.verdictForDecodeFailure(new OutOfMemoryError("Java heap space"));
        assertThat(oom.verdict()).isEqualTo(ImageForeground.Verdict.DECODE_FAILED);
        assertThat(ImageForeground.verdictForDecodeFailure(new IOException("skip")).verdict())
                .isEqualTo(ImageForeground.Verdict.OK);
    }

    @Test
    void tinyFakePngIsNotBlank() {
        byte[] fake = {
                (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01, 0x02, 0x03
        };
        assertThat(ImageForeground.inspect(fake).verdict()).isEqualTo(ImageForeground.Verdict.OK);
        assertThat(ImageForeground.isBlankCanvas(fake)).isFalse();
    }

    @Test
    void solidBeigeCanvasIsBlank() throws Exception {
        assertThat(ImageForeground.inspect(solidPng(64, 80, new Color(214, 206, 196))).verdict())
                .isEqualTo(ImageForeground.Verdict.BLANK);
    }

    @Test
    void colorfulSmallGarmentIsNotBlank() throws Exception {
        BufferedImage image = new BufferedImage(64, 80, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(232, 226, 214));
        graphics.fillRect(0, 0, 64, 80);
        graphics.setColor(new Color(36, 92, 178));
        graphics.fillRect(20, 18, 24, 40);
        graphics.dispose();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ImageIO.write(image, "png", buffer);
        assertThat(ImageForeground.inspect(buffer.toByteArray()).verdict())
                .isEqualTo(ImageForeground.Verdict.OK);
    }

    @Test
    void phoneSizedDimensionsAllowed() {
        assertThat(ImageForeground.exceedsLimit(4032, 3024)).isFalse();
        assertThat(ImageForeground.exceedsLimit(8193, 100)).isTrue();
        assertThat(ImageForeground.exceedsLimit(5000, 5000)).isTrue();
    }

    private static byte[] solidPng(int width, int height, Color color) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ImageIO.write(image, "png", buffer);
        return buffer.toByteArray();
    }
}
