package app.aura.backend.web;

import app.aura.backend.support.ImageForeground;
import app.aura.backend.support.ImageForeground.Inspection;
import app.aura.backend.support.ImageForeground.Verdict;

/**
 * Header-seviyeli gorsel kapisi — Wardrobe ve VTON ayni karari kullanir.
 */
public final class ImageSafetyGate {

    private ImageSafetyGate() {}

    public static void rejectUnsafeHeader(byte[] bytes) {
        Inspection header = ImageForeground.inspectHeader(bytes);
        if (header.verdict() == Verdict.TOO_LARGE) {
            throw new ImageTooLargeException(header.width(), header.height());
        }
        if (header.verdict() == Verdict.DECODE_FAILED) {
            throw new UnusableGarmentException(
                    "decode_failed",
                    "Gorsel okunamadi, lutfen baska bir fotoğraf deneyin");
        }
    }

    public static void rejectUnusablePixels(byte[] bytes) {
        Inspection inspection = ImageForeground.inspect(bytes);
        switch (inspection.verdict()) {
            case TOO_LARGE -> throw new ImageTooLargeException(
                    inspection.width(), inspection.height());
            case DECODE_FAILED -> throw new UnusableGarmentException(
                    "decode_failed",
                    "Gorsel okunamadi, lutfen baska bir fotoğraf deneyin");
            case BLANK -> throw new UnusableGarmentException(
                    "empty_mask",
                    "Arka planı ayırt edemedik, lütfen daha sade bir zeminde çekin");
            case OK -> {
                // yazılabilir
            }
        }
    }
}
