package app.aura.backend.web;

/** Vision cutout bos/carsaf veya Java on-kontrolu: dolaba yazilmaz. */
public class UnusableGarmentException extends RuntimeException {

    private final String rejectedReason;

    public UnusableGarmentException(String rejectedReason, String message) {
        super(message);
        this.rejectedReason = rejectedReason == null || rejectedReason.isBlank()
                ? "empty_mask"
                : rejectedReason;
    }

    public String getRejectedReason() {
        return rejectedReason;
    }
}
