package pl.piomin.services.backend.audit;

/**
 * Thrown by {@code AuditService.submit} when a {@code PENDING} request already exists for the
 * same {@code (entityType, entityId)} pair. This dedup rule is fully generic — keyed only on
 * those two fields, no entity-specific knowledge required. Mapped to {@code 409} by
 * {@code GlobalExceptionHandler}.
 */
public class DuplicatePendingAuditRequestException extends RuntimeException {

    private final String entityType;
    private final Long entityId;

    public DuplicatePendingAuditRequestException(String entityType, Long entityId) {
        super("A pending audit request already exists for " + entityType + "#" + entityId);
        this.entityType = entityType;
        this.entityId = entityId;
    }

    public String getEntityType() {
        return entityType;
    }

    public Long getEntityId() {
        return entityId;
    }
}
