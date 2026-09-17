package app.aura.backend.web;

/**
 * VTON worker (yerel FastAPI veya RunPod) erisilemiyor — timeout / ag / cold-start.
 */
public class VtonWorkerUnavailableException extends RuntimeException {

    public VtonWorkerUnavailableException(String message) {
        super(message);
    }

    public VtonWorkerUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
