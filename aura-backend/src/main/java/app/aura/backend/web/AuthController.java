package app.aura.backend.web;

import app.aura.backend.dto.AuthSessionResponse;
import app.aura.backend.dto.AuthTokenRequest;
import app.aura.backend.dto.AuthTokenResponse;
import app.aura.backend.dto.LoginRequest;
import app.aura.backend.dto.LogoutRequest;
import app.aura.backend.dto.RefreshRequest;
import app.aura.backend.dto.RegisterRequest;
import app.aura.backend.dto.RegisterResponse;
import app.aura.backend.security.SecurityUtils;
import app.aura.backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Auth HTTP arayuzu — gercek register/login + deprecated demo token.
 */
@RestController
@RequestMapping("/api/v1/aura/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * @deprecated v0.18.0 — sifresiz demo token. Test/geriye uyumluluk icin tutuluyor.
     */
    @Deprecated(since = "0.18.0", forRemoval = false)
    @PostMapping("/token")
    public AuthTokenResponse token(@RequestBody(required = false) AuthTokenRequest request) {
        return authService.issueToken(request == null ? new AuthTokenRequest(null, null) : request);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthSessionResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public AuthSessionResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody LogoutRequest request) {
        Long userId = SecurityUtils.requireUserId();
        authService.logout(userId, request, extractBearer(authorization));
        return ResponseEntity.noContent().build();
    }

    private static String extractBearer(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        if (authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return authorization.substring(7).trim();
        }
        return authorization.trim();
    }
}
