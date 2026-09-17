package app.aura.backend.web;

/**
 * Gunluk VTON kotasi asildi — HTTP 429.
 */
public class VtonQuotaExceededException extends RuntimeException {

    public VtonQuotaExceededException(String message) {
        super(message);
    }
}
