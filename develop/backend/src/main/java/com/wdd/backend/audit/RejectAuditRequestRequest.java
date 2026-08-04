package com.wdd.backend.audit;

import jakarta.validation.constraints.NotBlank;

public class RejectAuditRequestRequest {

    private String reviewedBy;

    @NotBlank
    private String rejectReason;

    public RejectAuditRequestRequest() {
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(String reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }
}
