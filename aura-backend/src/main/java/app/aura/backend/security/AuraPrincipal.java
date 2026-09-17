package app.aura.backend.security;

/**
 * JWT AuthenticatedPrincipal — SecurityContext'teki kimlik.
 */
public record AuraPrincipal(Long userId, String username) {
}
