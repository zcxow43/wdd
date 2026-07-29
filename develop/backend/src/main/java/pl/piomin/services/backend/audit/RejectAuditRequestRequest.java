package pl.piomin.services.backend.audit;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for rejecting an audit request. {@code reviewedBy} is optional
 * free-text; {@code rejectReason} is required and must be non-blank.
 */
public class RejectAuditRequestRequest {

    private String reviewedBy;

    @NotBlank(message = "rejectReason is required")
    private String rejectReason;

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
