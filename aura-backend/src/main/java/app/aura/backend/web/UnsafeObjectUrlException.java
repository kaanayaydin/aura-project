package app.aura.backend.web;

/** personImageUrl / object URL allowlist disi — SSRF. */
public class UnsafeObjectUrlException extends RuntimeException {

    private final String rejectedReason;

    public UnsafeObjectUrlException(String message) {
        this("unsafe_url", message);
    }

    public UnsafeObjectUrlException(String rejectedReason, String message) {
        super(message);
        this.rejectedReason = rejectedReason;
    }

    public String getRejectedReason() {
        return rejectedReason;
    }
}
