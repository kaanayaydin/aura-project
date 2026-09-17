package app.aura.backend.dto;

/**
 * Kayit sonrasi ozet cevap.
 */
public record RegisterResponse(
        Long userId,
        String email,
        String username,
        boolean emailVerified) {
}
