package app.aura.backend.web;

/** Favori kombin bulunamadi. */
public class FavoriteNotFoundException extends RuntimeException {

    public FavoriteNotFoundException(String message) {
        super(message);
    }
}
