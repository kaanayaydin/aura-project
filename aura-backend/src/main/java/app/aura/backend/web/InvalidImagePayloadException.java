package app.aura.backend.web;

/** Gonderilen base64 gorsel bozuk, bos veya sinirlari asiyor. */
public class InvalidImagePayloadException extends RuntimeException {

    public InvalidImagePayloadException(String message) {
        super(message);
    }
}
