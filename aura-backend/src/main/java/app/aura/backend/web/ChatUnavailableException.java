package app.aura.backend.web;

/**
 * Ollama erisilemez ve fallback kapaliysa.
 */
public class ChatUnavailableException extends RuntimeException {

    public ChatUnavailableException(String message) {
        super(message);
    }

    public ChatUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
