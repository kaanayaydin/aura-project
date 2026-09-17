package app.aura.backend.security;

import app.aura.backend.web.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Spring Security Context helper — VTON kimligi JWT Principal'dan.
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static AuraPrincipal requirePrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuraPrincipal principal)) {
            throw new UnauthorizedException("Kimlik dogrulamasi gerekli (Bearer JWT).");
        }
        return principal;
    }

    public static Long requireUserId() {
        return requirePrincipal().userId();
    }
}
