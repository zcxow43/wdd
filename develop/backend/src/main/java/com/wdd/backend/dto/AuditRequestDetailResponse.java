package com.wdd.backend.dto;

import java.time.LocalDateTime;

/**
 * Response shape for {@code GET /api/audit-requests/{id}} and the
 * approve/reject/cancel actions — the full record including
 * {@code beforeData}/{@code afterData}, returned verbatim as stored.
 */
public class AuditRequestDetailResponse {

    private Long id;
    private String entityType;
    private String actionType;
    private Long entityId;
    private Long brandId;
    private String summary;
    private Object beforeData;
    private Object afterData;
    private String status;
    private String requestedBy;
    private LocalDateTime requestedAt;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private String reviewComment;
    private String applyError;

    public AuditRequestDetailResponse() {
    }

    public AuditRequestDetailResponse(Long id, String entityType, String actionType, Long entityId, Long brandId,
            String summary, Object beforeData, Object afterData, String status, String requestedBy,
            LocalDateTime requestedAt, String reviewedBy, LocalDateTime reviewedAt, String reviewComment,
            String applyError) {
        this.id = id;
        this.entityType = entityType;
        this.actionType = actionType;
        this.entityId = entityId;
        this.brandId = brandId;
        this.summary = summary;
        this.beforeData = beforeData;
        this.afterData = afterData;
        this.status = status;
        this.requestedBy = requestedBy;
        this.requestedAt = requestedAt;
        this.reviewedBy = reviewedBy;
        this.reviewedAt = reviewedAt;
        this.reviewComment = reviewComment;
        this.applyError = applyError;
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

    public Long getBrandId() {
        return brandId;
    }

    public void setBrandId(Long brandId) {
        this.brandId = brandId;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Object getBeforeData() {
        return beforeData;
    }

    public void setBeforeData(Object beforeData) {
        this.beforeData = beforeData;
    }

    public Object getAfterData() {
        return afterData;
    }

    public void setAfterData(Object afterData) {
        this.afterData = afterData;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public String getReviewComment() {
        return reviewComment;
    }

    public void setReviewComment(String reviewComment) {
        this.reviewComment = reviewComment;
    }

    public String getApplyError() {
        return applyError;
    }

    public void setApplyError(String applyError) {
        this.applyError = applyError;
    }
}
