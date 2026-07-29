package pl.piomin.services.backend.audit;

import java.time.LocalDateTime;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Response DTO returned by all {@code /api/audit-requests} endpoints.
 * {@code before}/{@code after} are the raw stored JSON, deserialized to a
 * generic {@code Map<String, Object>} and returned as-is — this module never
 * inspects their contents beyond passing them to the relevant {@link AuditHandler}.
 */
public class AuditRequestResponse {

    private static final ObjectMapper JSON = new ObjectMapper();

    private Long id;
    private String entityType;
    private String actionType;
    private Long entityId;
    private String status;
    private String summary;
    private Object before;
    private Object after;
    private String requestedBy;
    private LocalDateTime requestedAt;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private String rejectReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AuditRequestResponse from(AuditRequest request) {
        AuditRequestResponse response = new AuditRequestResponse();
        response.id = request.getId();
        response.entityType = request.getEntityType();
        response.actionType = request.getActionType();
        response.entityId = request.getEntityId();
        response.status = request.getStatus();
        response.summary = request.getSummary();
        response.before = parseJson(request.getBeforeSnapshot());
        response.after = parseJson(request.getAfterSnapshot());
        response.requestedBy = request.getRequestedBy();
        response.requestedAt = request.getRequestedAt();
        response.reviewedBy = request.getReviewedBy();
        response.reviewedAt = request.getReviewedAt();
        response.rejectReason = request.getRejectReason();
        response.createdAt = request.getCreatedAt();
        response.updatedAt = request.getUpdatedAt();
        return response;
    }

    @SuppressWarnings("unchecked")
    private static Object parseJson(String json) {
        if (json == null) {
            return null;
        }
        try {
            return JSON.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid JSON snapshot stored on audit request", e);
        }
    }

    public Long getId() {
        return id;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getActionType() {
        return actionType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public String getStatus() {
        return status;
    }

    public String getSummary() {
        return summary;
    }

    public Object getBefore() {
        return before;
    }

    public Object getAfter() {
        return after;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
