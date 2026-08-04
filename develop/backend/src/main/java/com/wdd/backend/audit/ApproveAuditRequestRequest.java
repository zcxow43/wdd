package com.wdd.backend.audit;

public class ApproveAuditRequestRequest {

    private String reviewedBy;

    public ApproveAuditRequestRequest() {
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(String reviewedBy) {
        this.reviewedBy = reviewedBy;
    }
}
