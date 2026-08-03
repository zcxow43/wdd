package pl.piomin.services.backend.audit;

/**
 * The kind of change an {@link AuditRequest} proposes for the target entity.
 */
public enum AuditActionType {
    CREATE,
    UPDATE,
    DELETE
}
