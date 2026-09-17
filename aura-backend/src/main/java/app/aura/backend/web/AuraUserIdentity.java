package app.aura.backend.web;

/**
 * @deprecated v0.17.1 — VTON kimligi JWT Principal ({@code SecurityUtils}) uzerinden gelir.
 * {@code X-Aura-User-Id} / {@code ?userId=} artik guvenilmez.
 */
@Deprecated(since = "0.17.1", forRemoval = true)
public final class AuraUserIdentity {

    public static final String USER_ID_HEADER = "X-Aura-User-Id";

    private AuraUserIdentity() {
    }

    public static Long resolve(String headerValue, Long queryUserId) {
        if (headerValue != null && !headerValue.isBlank()) {
            try {
                return Long.parseLong(headerValue.trim());
            } catch (NumberFormatException exception) {
                throw new InvalidAuraIdentityException(
                        "Gecersiz " + USER_ID_HEADER + " degeri: " + headerValue);
            }
        }
        return queryUserId;
    }
}
