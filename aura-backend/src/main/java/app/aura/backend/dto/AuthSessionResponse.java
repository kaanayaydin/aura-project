package app.aura.backend.dto;

/**
 * Login / refresh oturum cevabi.
 *
 * @param expiresIn access token omru (saniye)
 */
public record AuthSessionResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        Long userId,
        String email) {
}
