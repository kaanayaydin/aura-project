package app.aura.backend.dto;

/**
 * Demo / istemci token istegi.
 *
 * @param username yoksa aura.wardrobe.default-username
 * @param userId   varsa dogrudan bu kullanici (test kolayligi)
 */
public record AuthTokenRequest(String username, Long userId) {
}
