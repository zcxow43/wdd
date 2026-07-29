package pl.piomin.services.backend.audit;

/**
 * The kind of change an {@link AuditRequest} proposes. Shared, generic vocabulary
 * used by every {@link AuditHandler} implementation regardless of entity type.
 */
public enum AuditActionType {
    CREATE,
    UPDATE,
    DELETE
}
