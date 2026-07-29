package pl.piomin.services.backend.audit;

/**
 * Request DTO for approving an audit request. {@code reviewedBy} is optional
 * free-text (no authentication system exists in this app).
 */
public class ApproveAuditRequestRequest {

    private String reviewedBy;

    public String getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(String reviewedBy) {
        this.reviewedBy = reviewedBy;
    }
}
