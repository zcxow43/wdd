package com.wdd.backend.exception;

/**
 * Thrown when a sync is attempted less than 60 real-world seconds after the
 * last successful one. Carries the number of seconds remaining (rounded up)
 * so the caller can report {@code retryAfterSeconds}.
 */
public class ExchangeRateSyncCooldownException extends RuntimeException {

    private final long retryAfterSeconds;

    public ExchangeRateSyncCooldownException(long retryAfterSeconds) {
        super("匯率同步一分鐘內僅能執行一次，請稍後再試");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
