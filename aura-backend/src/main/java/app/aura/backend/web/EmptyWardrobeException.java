package app.aura.backend.web;

/** Oneri uretilemeyecek kadar bos dolap. */
public class EmptyWardrobeException extends RuntimeException {

    public EmptyWardrobeException(String message) {
        super(message);
    }
}
