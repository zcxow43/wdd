package com.wdd.backend.exception;

/**
 * 422 — the registered handler rejected the request at approval time
 * because the target data had drifted since it was raised. The request
 * stays {@code PENDING}; the caller may retry after resolving the drift, or
 * cancel.
 */
public class AuditApplyFailedException extends RuntimeException {

    private final Long auditRequestId;

    public AuditApplyFailedException(Long auditRequestId, String message) {
        super(message);
        this.auditRequestId = auditRequestId;
    }

    public Long getAuditRequestId() {
        return auditRequestId;
    }
}
