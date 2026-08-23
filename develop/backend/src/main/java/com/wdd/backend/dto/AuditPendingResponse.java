package com.wdd.backend.dto;

/**
 * Response body for an audited write that has been submitted but not yet
 * applied — returned with {@code 202} by any controller endpoint that,
 * instead of writing directly, calls {@link com.wdd.backend.service.AuditService#submit}.
 * Generic across every audited entity (e.g. {@code CURRENCY_PAIR}): built
 * straight from the {@link AuditRequest} the submit call returns.
 */
public class AuditPendingResponse {

    private Long auditRequestId;
    private String status;
    private String entityType;
    private String actionType;
    private Long entityId;
    private String summary;

    public AuditPendingResponse() {
    }

    public AuditPendingResponse(Long auditRequestId, String status, String entityType, String actionType,
            Long entityId, String summary) {
        this.auditRequestId = auditRequestId;
        this.status = status;
        this.entityType = entityType;
        this.actionType = actionType;
        this.entityId = entityId;
        this.summary = summary;
    }

    public static AuditPendingResponse from(AuditRequest request) {
        return new AuditPendingResponse(
                request.getId(),
                request.getStatus(),
                request.getEntityType(),
                request.getActionType(),
                request.getEntityId(),
                request.getSummary());
    }

    public Long getAuditRequestId() {
        return auditRequestId;
    }

    public void setAuditRequestId(Long auditRequestId) {
        this.auditRequestId = auditRequestId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public void setEntityId(Long entityId) {
        this.entityId = entityId;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }
}
