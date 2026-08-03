package pl.piomin.services.backend.audit;

/**
 * Request body for {@code POST /api/audit-requests/{id}/approve}. {@code reviewedBy} is
 * optional free-text — no auth system exists in this app.
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
