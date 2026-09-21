package app.aura.backend.web;

import app.aura.backend.support.ImageForeground;

/** Header'daki genişlik×yükseklik decode edilmeden reddedildi. */
public class ImageTooLargeException extends RuntimeException {

    private final int width;
    private final int height;

    public ImageTooLargeException(int width, int height) {
        super("Gorsel cozunurlugu cok buyuk (%dx%d, en fazla %d MP)."
                .formatted(width, height, ImageForeground.MAX_PIXELS / 1_000_000L));
        this.width = width;
        this.height = height;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public String getRejectedReason() {
        return "image_too_large";
    }
}
