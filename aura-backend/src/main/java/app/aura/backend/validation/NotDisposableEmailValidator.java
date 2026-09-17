package app.aura.backend.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Locale;
import java.util.Set;

/**
 * Bilinen disposable / throwaway email domainlerini reddeder.
 */
public class NotDisposableEmailValidator implements ConstraintValidator<NotDisposableEmail, String> {

    private static final Set<String> BLOCKED_DOMAINS = Set.of(
            "mailinator.com",
            "guerrillamail.com",
            "guerrillamail.net",
            "10minutemail.com",
            "tempmail.com",
            "temp-mail.org",
            "throwaway.email",
            "yopmail.com",
            "trashmail.com",
            "getnada.com",
            "sharklasers.com",
            "discard.email",
            "fakeinbox.com",
            "maildrop.cc",
            "mailnesia.com");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true; // @NotBlank / @Email ayri kontrol eder
        }
        String email = value.trim().toLowerCase(Locale.ROOT);
        int at = email.lastIndexOf('@');
        if (at < 1 || at == email.length() - 1) {
            return true;
        }
        String domain = email.substring(at + 1);
        return !BLOCKED_DOMAINS.contains(domain);
    }
}
