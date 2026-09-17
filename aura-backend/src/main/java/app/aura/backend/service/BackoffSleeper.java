package app.aura.backend.service;

/**
 * Cold-start backoff icin test edilebilir uyku.
 */
@FunctionalInterface
interface BackoffSleeper {

    void sleep(long millis) throws InterruptedException;

    BackoffSleeper THREAD = millis -> {
        if (millis > 0) {
            Thread.sleep(millis);
        }
    };
}
