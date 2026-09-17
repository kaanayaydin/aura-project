package app.aura.backend.web;

/** Katalogda olmayan veya zaten rafta olan parfum. */
public class PerfumeShelfException extends RuntimeException {

    private final int status;

    public PerfumeShelfException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
