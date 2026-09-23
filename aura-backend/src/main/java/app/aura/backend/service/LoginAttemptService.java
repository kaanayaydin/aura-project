package app.aura.backend.service;

import app.aura.backend.config.AuthProperties;
import app.aura.backend.model.AccountStatus;
import app.aura.backend.model.User;
import app.aura.backend.repository.UserRepository;
import app.aura.backend.web.AccountLockedException;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Basarisiz giris sayaci ve hesap kilitleme.
 * Degisiklikler REQUIRES_NEW ile commit edilir (dis login TX rollback etse bile).
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    private final UserRepository userRepository;
    private final AuthProperties authProperties;

    public LoginAttemptService(UserRepository userRepository, AuthProperties authProperties) {
        this.userRepository = userRepository;
        this.authProperties = authProperties;
    }

    /**
     * Kilit suresi dolduysa ACTIVE'e cevirir; hâlâ kilitliyse 423 firlatir.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void assertNotLocked(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        if (user.getAccountStatus() == AccountStatus.DISABLED) {
            throw new AccountLockedException("Hesap devre disi birakilmis.");
        }
        if (user.getAccountStatus() == AccountStatus.LOCKED) {
            Instant until = user.getLockedUntil();
            if (until != null && until.isAfter(Instant.now())) {
                throw new AccountLockedException(
                        "Hesap gecici olarak kilitli. Tekrar deneyin: " + until);
            }
            user.setAccountStatus(AccountStatus.ACTIVE);
            user.setLockedUntil(null);
            user.setFailedLoginAttempts(0);
            userRepository.save(user);
            log.info("Hesap kilidi kaldirildi: userId={}", user.getId());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onLoginSuccess(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        user.setLastLoginAt(Instant.now());
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        if (user.getAccountStatus() == AccountStatus.LOCKED) {
            user.setAccountStatus(AccountStatus.ACTIVE);
        }
        userRepository.save(user);
    }

    /**
     * @return true ise hesap yeni kilitlendi
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean onLoginFailure(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        boolean locked = false;
        if (attempts >= authProperties.maxFailedAttempts()) {
            Instant until = Instant.now().plusSeconds(authProperties.lockDurationMinutes() * 60L);
            user.setAccountStatus(AccountStatus.LOCKED);
            user.setLockedUntil(until);
            locked = true;
            log.warn("Hesap kilitlendi: userId={} until={}", user.getId(), until);
        }
        userRepository.save(user);
        return locked;
    }

    public int lockDurationMinutes() {
        return authProperties.lockDurationMinutes();
    }
}
