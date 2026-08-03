package pl.piomin.services.backend.audit;

import java.time.LocalDateTime;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Generic response shape for the audit REST API. {@code before}/{@code after} are the raw
 * stored JSON, deserialized to a generic {@code Map<String, Object>} and returned as-is — this
 * module never inspects their contents beyond passing them to the relevant handler.
 */
public class AuditRequestResponse {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private Long id;
    private String entityType;
    private String actionType;
    private Long entityId;
    private String status;
    private String summary;
    private Map<String, Object> before;
    private Map<String, Object> after;
    private String requestedBy;
    private LocalDateTime requestedAt;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private String rejectReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AuditRequestResponse from(AuditRequest entity) {
        AuditRequestResponse response = new AuditRequestResponse();
        response.id = entity.getId();
        response.entityType = entity.getEntityType();
        response.actionType = entity.getActionType();
        response.entityId = entity.getEntityId();
        response.status = entity.getStatus();
        response.summary = entity.getSummary();
        response.before = readJson(entity.getBeforeSnapshot());
        response.after = readJson(entity.getAfterSnapshot());
        response.requestedBy = entity.getRequestedBy();
        response.requestedAt = entity.getRequestedAt();
        response.reviewedBy = entity.getReviewedBy();
        response.reviewedAt = entity.getReviewedAt();
        response.rejectReason = entity.getRejectReason();
        response.createdAt = entity.getCreatedAt();
        response.updatedAt = entity.getUpdatedAt();
        return response;
    }

    private static Map<String, Object> readJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return JSON.readValue(raw, MAP_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt audit snapshot JSON", e);
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

    public Map<String, Object> getBefore() {
        return before;
    }

    public Map<String, Object> getAfter() {
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
