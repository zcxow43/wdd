package com.wdd.backend.dto;

import java.time.LocalDateTime;

/**
 * Persistence model mapped to the {@code audit_request} table. This module
 * treats {@code beforeData}/{@code afterData} as opaque JSON — it never
 * interprets their contents, only stores and returns them verbatim. Entity
 * knowledge lives entirely in the {@link com.wdd.backend.service.AuditHandler}
 * registered for {@code entityType}.
 */
public class AuditRequest {

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
