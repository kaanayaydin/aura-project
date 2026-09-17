package app.aura.backend.dto;

/**
 * JWT access token cevabi.
 */
public record AuthTokenResponse(
        String accessToken,
        String tokenType,
        long expiresInMinutes,
        Long userId,
        String username) {
}
