package app.aura.backend.service;

import app.aura.backend.config.AuraProperties;
import app.aura.backend.dto.AuthSessionResponse;
import app.aura.backend.dto.AuthTokenRequest;
import app.aura.backend.dto.AuthTokenResponse;
import app.aura.backend.dto.LoginRequest;
import app.aura.backend.dto.LogoutRequest;
import app.aura.backend.dto.RefreshRequest;
import app.aura.backend.dto.RegisterRequest;
import app.aura.backend.dto.RegisterResponse;
import app.aura.backend.model.AccountStatus;
import app.aura.backend.model.User;
import app.aura.backend.repository.UserRepository;
import app.aura.backend.security.JwtService;
import app.aura.backend.security.TokenBlacklistService;
import app.aura.backend.web.AccountLockedException;
import app.aura.backend.web.AuthConflictException;
import app.aura.backend.web.UnauthorizedException;
import app.aura.backend.web.UserNotFoundException;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Auth orkestrasyonu — gercek register/login + deprecated demo token.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String GENERIC_LOGIN_ERROR = "Email veya sifre hatali.";

    private final UserRepository userRepository;
    private final AuraProperties auraProperties;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final LoginAttemptService loginAttemptService;
    private final TokenBlacklistService tokenBlacklistService;

    public AuthService(
            UserRepository userRepository,
            AuraProperties auraProperties,
            JwtService jwtService,
            PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService,
            LoginAttemptService loginAttemptService,
            TokenBlacklistService tokenBlacklistService) {
        this.userRepository = userRepository;
        this.auraProperties = auraProperties;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.loginAttemptService = loginAttemptService;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    /**
     * @deprecated v0.18.0 — sifresiz demo token. Test/geriye uyumluluk icin tutuluyor.
     *             Yeni istemciler {@link #login(LoginRequest)} kullanmali.
     */
    @Deprecated(since = "0.18.0", forRemoval = false)
    @Transactional
    public AuthTokenResponse issueToken(AuthTokenRequest request) {
        User user = resolveDemoUser(request);
        String token = jwtService.issueToken(user.getId(), user.getUsername());
        log.info("Demo JWT uretildi: userId={} username={}", user.getId(), user.getUsername());
        return new AuthTokenResponse(
                token,
                "Bearer",
                jwtService.expirationMinutes(),
                user.getId(),
                user.getUsername());
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new AuthConflictException("Bu email zaten kayitli.");
        }
        String username = deriveUsername(email);
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            username = username + "_" + System.currentTimeMillis() % 10_000;
        }

        User user = new User(username, email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setEmailVerified(false);
        user.setAccountStatus(AccountStatus.ACTIVE);
        user = userRepository.save(user);
        log.info("Kullanici kaydedildi: id={} email={}", user.getId(), email);
        return new RegisterResponse(user.getId(), user.getEmail(), user.getUsername(), false);
    }

    @Transactional
    public AuthSessionResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null || user.getPasswordHash() == null) {
            throw new UnauthorizedException(GENERIC_LOGIN_ERROR);
        }

        loginAttemptService.assertNotLocked(user.getId());

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            boolean locked = loginAttemptService.onLoginFailure(user.getId());
            if (locked) {
                throw new AccountLockedException(
                        "Cok fazla basarisiz deneme. Hesap %d dakika kilitlendi."
                                .formatted(loginAttemptService.lockDurationMinutes()));
            }
            throw new UnauthorizedException(GENERIC_LOGIN_ERROR);
        }

        loginAttemptService.onLoginSuccess(user.getId());
        // reload after attempt counter reset
        user = userRepository.findById(user.getId()).orElseThrow();
        return issueSession(user);
    }

    @Transactional
    public AuthSessionResponse refresh(RefreshRequest request) {
        var rotated = refreshTokenService.rotate(request.refreshToken());
        User user = rotated.entity().getUser();
        String access = jwtService.issueToken(user.getId(), user.getUsername());
        return new AuthSessionResponse(
                access,
                rotated.rawToken(),
                "Bearer",
                jwtService.expirationSeconds(),
                user.getId(),
                user.getEmail());
    }

    @Transactional
    public void logout(Long authenticatedUserId, LogoutRequest request, String rawAccessToken) {
        refreshTokenService.revokeRawTokenForUser(request.refreshToken(), authenticatedUserId);
        if (rawAccessToken != null && !rawAccessToken.isBlank()) {
            Instant expiresAt = jwtService.expirationOf(rawAccessToken);
            tokenBlacklistService.blacklistUntil(rawAccessToken, expiresAt);
        }
        log.info("Logout: userId={} accessToken blacklisted", authenticatedUserId);
    }

    private AuthSessionResponse issueSession(User user) {
        String access = jwtService.issueToken(user.getId(), user.getUsername());
        var refresh = refreshTokenService.issue(user);
        return new AuthSessionResponse(
                access,
                refresh.rawToken(),
                "Bearer",
                jwtService.expirationSeconds(),
                user.getId(),
                user.getEmail());
    }

    private User resolveDemoUser(AuthTokenRequest request) {
        if (request != null && request.userId() != null) {
            return userRepository.findById(request.userId())
                    .orElseThrow(() -> new UserNotFoundException(
                            "Kullanici bulunamadi: %d".formatted(request.userId())));
        }
        String username = request != null && request.username() != null && !request.username().isBlank()
                ? request.username().trim()
                : auraProperties.defaultUsername();
        return userRepository.findByUsername(username)
                .orElseGet(() -> {
                    log.info("Auth icin varsayilan kullanici olusturuluyor: {}", username);
                    return userRepository.save(new User(username, username + "@aura.local"));
                });
    }

    private static String deriveUsername(String email) {
        String local = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        String cleaned = local.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (cleaned.isBlank()) {
            cleaned = "user";
        }
        if (cleaned.length() > 100) {
            cleaned = cleaned.substring(0, 100);
        }
        return cleaned;
    }
}
