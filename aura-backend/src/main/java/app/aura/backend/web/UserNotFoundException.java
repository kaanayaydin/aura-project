package app.aura.backend.web;

/** Istekte belirtilen kullanici veritabaninda yok. */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String message) {
        super(message);
    }
}
