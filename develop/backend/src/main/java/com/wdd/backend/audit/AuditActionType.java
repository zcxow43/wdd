package com.wdd.backend.audit;

/**
 * The kind of change an {@link AuditRequest} proposes against the target entity.
 */
public enum AuditActionType {
    CREATE,
    UPDATE,
    DELETE
}
