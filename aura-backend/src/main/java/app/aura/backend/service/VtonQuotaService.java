package app.aura.backend.service;

import app.aura.backend.config.VtonProperties;
import app.aura.backend.model.User;
import app.aura.backend.repository.UserRepository;
import app.aura.backend.web.VtonQuotaExceededException;
import java.time.Clock;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kullanici bazli gunluk VTON kotasi — sifirlama, tuketim, iade.
 */
@Service
public class VtonQuotaService {

    private static final Logger log = LoggerFactory.getLogger(VtonQuotaService.class);

    public static final String DAILY_LIMIT_MESSAGE = "Günlük sanal deneme limitinize ulaştınız";

    private final UserRepository userRepository;
    private final VtonProperties vtonProperties;
    private final Clock clock;

    public VtonQuotaService(
            UserRepository userRepository, VtonProperties vtonProperties, Clock clock) {
        this.userRepository = userRepository;
        this.vtonProperties = vtonProperties;
        this.clock = clock;
    }

    /**
     * Kotayi kontrol eder ve 1 hak dusurur. Limit doluysa 429.
     */
    @Transactional
    public void consume(User user) {
        resetIfNeeded(user);
        int limit = vtonProperties.dailyLimit();
        if (user.getDailyVtonCount() >= limit) {
            throw new VtonQuotaExceededException(DAILY_LIMIT_MESSAGE);
        }
        user.setDailyVtonCount(user.getDailyVtonCount() + 1);
        userRepository.save(user);
        log.debug(
                "VTON kota tuketildi: userId={} count={}/{}",
                user.getId(),
                user.getDailyVtonCount(),
                limit);
    }

    /**
     * Basarisiz is / worker hatasi sonrasi hak iadesi.
     */
    @Transactional
    public void refund(User user) {
        resetIfNeeded(user);
        if (user.getDailyVtonCount() <= 0) {
            return;
        }
        user.setDailyVtonCount(user.getDailyVtonCount() - 1);
        userRepository.save(user);
        log.info(
                "VTON kota iade edildi: userId={} count={}",
                user.getId(),
                user.getDailyVtonCount());
    }

    @Transactional
    public void resetIfNeeded(User user) {
        LocalDate today = today();
        LocalDate last = user.getLastVtonResetDate();
        if (last == null || last.isBefore(today)) {
            if (user.getDailyVtonCount() != 0 || last == null || !today.equals(last)) {
                log.debug(
                        "VTON gunluk sayac sifirlandi: userId={} onceki={} tarih={}",
                        user.getId(),
                        user.getDailyVtonCount(),
                        today);
            }
            user.setDailyVtonCount(0);
            user.setLastVtonResetDate(today);
            userRepository.save(user);
        }
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public int remaining(User user) {
        resetIfNeeded(user);
        return Math.max(0, vtonProperties.dailyLimit() - user.getDailyVtonCount());
    }
}
