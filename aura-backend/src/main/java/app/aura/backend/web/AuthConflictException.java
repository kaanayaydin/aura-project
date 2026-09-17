package app.aura.backend.web;

public class AuthConflictException extends RuntimeException {

    public AuthConflictException(String message) {
        super(message);
    }
}
