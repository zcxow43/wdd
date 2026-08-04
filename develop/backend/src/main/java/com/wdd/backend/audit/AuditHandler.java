package com.wdd.backend.audit;

import java.util.Map;

/**
 * Plug-in contract implemented once per approval-gated entity type. {@link AuditService}
 * holds a registry of all {@link AuditHandler} beans keyed by {@link #entityType()} and
 * never contains any entity-specific logic itself.
 */
public interface AuditHandler {

    /**
     * Unique key identifying the entity type this handler serves, e.g. {@code "CURRENCY_PAIR"}.
     * Must be unique across all registered handlers.
     */
    String entityType();

    /**
     * Build the "before" snapshot from the live entity, for an UPDATE/DELETE submission.
     * Throws the entity's own not-found exception if entityId doesn't exist.
     */
    Map<String, Object> snapshotOf(Long entityId);

    /**
     * Validate a proposed "after" snapshot for CREATE/UPDATE. Also responsible for any
     * entity-specific dedup/natural-key rule (e.g. a CREATE colliding with another live
     * row, or with another PENDING CREATE request of this same entityType).
     * Throws the entity's own validation exceptions (400/404/409) on failure. Not called
     * for DELETE (deleting has no field-level business rules beyond existence, which
     * AuditService already checks generically via snapshotOf).
     */
    void validate(AuditActionType actionType, Long entityId, Map<String, Object> afterSnapshot);

    /**
     * Apply an approved change to the real entity table: insert/update/delete.
     * Returns the entity's id (the new id for CREATE; entityId unchanged otherwise).
     */
    Long apply(AuditActionType actionType, Long entityId, Map<String, Object> afterSnapshot);

    /**
     * Short human-readable label for list rendering, e.g. "PUG · USD/TWD".
     */
    String summarize(Map<String, Object> snapshot);
}
