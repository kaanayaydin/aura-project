package app.aura.backend.web;

/** Istenen dolap parcasi veritabaninda yok. */
public class WardrobeItemNotFoundException extends RuntimeException {

    public WardrobeItemNotFoundException(String message) {
        super(message);
    }
}
