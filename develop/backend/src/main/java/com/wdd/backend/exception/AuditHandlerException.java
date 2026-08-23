package com.wdd.backend.exception;

/**
 * Thrown by an {@link com.wdd.backend.service.AuditHandler}'s
 * {@code validate}/{@code apply} to signal that the underlying data has
 * drifted since the request was raised, so the change can no longer be
 * legally applied. Caught by the audit service and surfaced as a {@code 422}
 * with the request left {@code PENDING} — see
 * {@link com.wdd.backend.exception.AuditApplyFailedException}.
 */
public class AuditHandlerException extends RuntimeException {

    public AuditHandlerException(String message) {
        super(message);
    }
}
