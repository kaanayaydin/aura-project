package app.aura.backend.web;

/** Takvim baglami (occasion) desteklenen degerlerden biri degil. */
public class InvalidOccasionException extends RuntimeException {

    public InvalidOccasionException(String message) {
        super(message);
    }
}
