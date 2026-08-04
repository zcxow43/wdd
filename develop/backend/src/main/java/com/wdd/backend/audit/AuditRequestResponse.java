package com.wdd.backend.audit;

import java.time.LocalDateTime;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class AuditRequestResponse {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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

    public AuditRequestResponse() {
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parse(String json) {
        if (json == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse stored audit snapshot JSON", e);
        }
    }

    public static AuditRequestResponse from(AuditRequest auditRequest) {
        AuditRequestResponse response = new AuditRequestResponse();
        response.setId(auditRequest.getId());
        response.setEntityType(auditRequest.getEntityType());
        response.setActionType(auditRequest.getActionType());
        response.setEntityId(auditRequest.getEntityId());
        response.setStatus(auditRequest.getStatus());
        response.setSummary(auditRequest.getSummary());
        response.setBefore(parse(auditRequest.getBeforeSnapshot()));
        response.setAfter(parse(auditRequest.getAfterSnapshot()));
        response.setRequestedBy(auditRequest.getRequestedBy());
        response.setRequestedAt(auditRequest.getRequestedAt());
        response.setReviewedBy(auditRequest.getReviewedBy());
        response.setReviewedAt(auditRequest.getReviewedAt());
        response.setRejectReason(auditRequest.getRejectReason());
        response.setCreatedAt(auditRequest.getCreatedAt());
        response.setUpdatedAt(auditRequest.getUpdatedAt());
        return response;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Map<String, Object> getBefore() {
        return before;
    }

    public void setBefore(Map<String, Object> before) {
        this.before = before;
    }

    public Map<String, Object> getAfter() {
        return after;
    }

    public void setAfter(Map<String, Object> after) {
        this.after = after;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(String requestedBy) {
        this.requestedBy = requestedBy;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(LocalDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(String reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
