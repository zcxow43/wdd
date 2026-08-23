package com.wdd.backend.exception;

/**
 * 409 — either a second {@code PENDING} request was submitted for a target
 * that already has one, or an already-resolved request was targeted by
 * approve/reject/cancel.
 */
public class AuditRequestConflictException extends RuntimeException {

    private AuditRequestConflictException(String message) {
        super(message);
    }

    public static AuditRequestConflictException pendingExists(String entityType, Long entityId) {
        return new AuditRequestConflictException(
                "A PENDING audit request already exists for " + entityType + ":" + entityId);
    }

    public static AuditRequestConflictException notPending(Long id, String currentStatus) {
        return new AuditRequestConflictException(
                "Audit request " + id + " is not PENDING (current status: " + currentStatus + ")");
    }
}
