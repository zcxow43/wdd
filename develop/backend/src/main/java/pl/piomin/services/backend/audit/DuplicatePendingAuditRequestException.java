package pl.piomin.services.backend.audit;

public class DuplicatePendingAuditRequestException extends RuntimeException {

    private final String entityType;
    private final Long entityId;

    public DuplicatePendingAuditRequestException(String entityType, Long entityId) {
        super("A pending audit request already exists for entityType=" + entityType + ", entityId=" + entityId);
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
