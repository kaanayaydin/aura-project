package app.aura.backend.web;

public class AuthRateLimitException extends RuntimeException {

    public AuthRateLimitException(String message) {
        super(message);
    }
}
