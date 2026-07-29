package pl.piomin.services.backend.audit;

import java.util.Map;

/**
 * The single extension point of the audit module. Any feature that needs its
 * create/update/delete gated behind review implements this interface once,
 * registers it as a Spring bean, and calls {@link AuditService#submit} from
 * its own controller/service. Nothing in the audit module itself (this
 * interface, {@link AuditService}, {@link AuditController}, or the
 * {@code audit_request} table) ever needs to change to support a new
 * implementation.
 */
public interface AuditHandler {

    /**
     * Unique key identifying the entity type this handler manages, e.g.
     * {@code "CURRENCY_PAIR"}. Must be unique across all registered handlers.
     */
    String entityType();

    /**
     * Builds the "before" snapshot from the live entity, for an UPDATE/DELETE
     * submission. Must throw the entity's own not-found exception if
     * {@code entityId} does not exist.
     */
    Map<String, Object> snapshotOf(Long entityId);

    /**
     * Validates a proposed "after" snapshot for CREATE/UPDATE. Also responsible
     * for any entity-specific dedup/natural-key rule (e.g. a CREATE colliding
     * with another live row, or with another PENDING CREATE request of this
     * same entity type). Throws the entity's own validation exceptions
     * (400/404/409) on failure. Not called for DELETE.
     */
    void validate(AuditActionType actionType, Long entityId, Map<String, Object> afterSnapshot);

    /**
     * Applies an approved change to the real entity table: insert/update/delete.
     * Returns the entity's id (the new id for CREATE; {@code entityId} unchanged
     * otherwise).
     */
    Long apply(AuditActionType actionType, Long entityId, Map<String, Object> afterSnapshot);

    /**
     * Short human-readable label for list rendering, e.g. {@code "PUG · USD/TWD"}.
     */
    String summarize(Map<String, Object> snapshot);
}
