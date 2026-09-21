package app.aura.backend.web;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Hatalari tutarli bir JSON govdesine (RFC 7807 ProblemDetail) cevirir.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    public ProblemDetail handleUserNotFound(UserNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Kullanici bulunamadi", exception.getMessage());
    }

    @ExceptionHandler(WardrobeItemNotFoundException.class)
    public ProblemDetail handleItemNotFound(WardrobeItemNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Dolap parcasi bulunamadi", exception.getMessage());
    }

    @ExceptionHandler(InvalidImagePayloadException.class)
    public ProblemDetail handleInvalidImage(InvalidImagePayloadException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Gecersiz gorsel yuku", exception.getMessage());
    }

    @ExceptionHandler(UnusableGarmentException.class)
    public ProblemDetail handleUnusableGarment(UnusableGarmentException exception) {
        ProblemDetail detail = problem(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Kiyafet kesilemedi",
                exception.getMessage());
        detail.setProperty("rejected_reason", exception.getRejectedReason());
        return detail;
    }

    @ExceptionHandler(InvalidOccasionException.class)
    public ProblemDetail handleInvalidOccasion(InvalidOccasionException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Gecersiz etkinlik baglami", exception.getMessage());
    }

    @ExceptionHandler(EmptyWardrobeException.class)
    public ProblemDetail handleEmptyWardrobe(EmptyWardrobeException exception) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Dolap yetersiz", exception.getMessage());
    }

    @ExceptionHandler(PerfumeShelfException.class)
    public ProblemDetail handlePerfumeShelf(PerfumeShelfException exception) {
        HttpStatus status = HttpStatus.resolve(exception.getStatus());
        if (status == null) {
            status = HttpStatus.BAD_REQUEST;
        }
        String title = switch (status) {
            case NOT_FOUND -> "Parfum bulunamadi";
            case CONFLICT -> "Parfum zaten rafta";
            default -> "Parfum rafi hatasi";
        };
        return problem(status, title, exception.getMessage());
    }

    @ExceptionHandler(FavoriteNotFoundException.class)
    public ProblemDetail handleFavoriteNotFound(FavoriteNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Favori bulunamadi", exception.getMessage());
    }

    @ExceptionHandler(ChatUnavailableException.class)
    public ProblemDetail handleChatUnavailable(ChatUnavailableException exception) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Aura AI erisilemiyor", exception.getMessage());
    }

    @ExceptionHandler(VtonOwnershipException.class)
    public ProblemDetail handleVtonOwnership(VtonOwnershipException exception) {
        return problem(HttpStatus.FORBIDDEN, "VTON erisim engeli", exception.getMessage());
    }

    @ExceptionHandler(VtonJobNotFoundException.class)
    public ProblemDetail handleVtonJobNotFound(VtonJobNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "VTON isi bulunamadi", exception.getMessage());
    }

    @ExceptionHandler(VtonWorkerUnavailableException.class)
    public ProblemDetail handleVtonWorkerUnavailable(VtonWorkerUnavailableException exception) {
        return problem(
                HttpStatus.SERVICE_UNAVAILABLE,
                "VTON worker erisilemiyor",
                exception.getMessage());
    }

    @ExceptionHandler(VtonQuotaExceededException.class)
    public ProblemDetail handleVtonQuotaExceeded(VtonQuotaExceededException exception) {
        return problem(
                HttpStatus.TOO_MANY_REQUESTS,
                "VTON kota limiti",
                exception.getMessage());
    }

    @ExceptionHandler(WardrobeOwnershipException.class)
    public ProblemDetail handleWardrobeOwnership(WardrobeOwnershipException exception) {
        return problem(HttpStatus.FORBIDDEN, "Dolap erisim engeli", exception.getMessage());
    }

    @ExceptionHandler(FavoriteOwnershipException.class)
    public ProblemDetail handleFavoriteOwnership(FavoriteOwnershipException exception) {
        return problem(HttpStatus.FORBIDDEN, "Favori erisim engeli", exception.getMessage());
    }

    @ExceptionHandler(PerfumeOwnershipException.class)
    public ProblemDetail handlePerfumeOwnership(PerfumeOwnershipException exception) {
        return problem(HttpStatus.FORBIDDEN, "Parfum erisim engeli", exception.getMessage());
    }

    @ExceptionHandler(AccountLockedException.class)
    public ProblemDetail handleAccountLocked(AccountLockedException exception) {
        return problem(HttpStatus.LOCKED, "Hesap kilitli", exception.getMessage());
    }

    @ExceptionHandler(AuthConflictException.class)
    public ProblemDetail handleAuthConflict(AuthConflictException exception) {
        return problem(HttpStatus.CONFLICT, "Kayit catismasi", exception.getMessage());
    }

    @ExceptionHandler(AuthRateLimitException.class)
    public ProblemDetail handleAuthRateLimit(AuthRateLimitException exception) {
        return problem(HttpStatus.TOO_MANY_REQUESTS, "Cok fazla istek", exception.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ProblemDetail handleUnauthorized(UnauthorizedException exception) {
        return problem(HttpStatus.UNAUTHORIZED, "Kimlik dogrulamasi gerekli", exception.getMessage());
    }

    @ExceptionHandler(InvalidAuraIdentityException.class)
    public ProblemDetail handleInvalidAuraIdentity(InvalidAuraIdentityException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Gecersiz kimlik", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage()));

        ProblemDetail detail = problem(
                HttpStatus.BAD_REQUEST, "Dogrulama hatasi", "Istek alanlari gecersiz.");
        detail.setProperty("fieldErrors", fieldErrors);
        return detail;
    }

    private ProblemDetail problem(HttpStatus status, String title, String detailMessage) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, detailMessage);
        detail.setTitle(title);
        detail.setProperty("timestamp", Instant.now().toString());
        return detail;
    }
}
