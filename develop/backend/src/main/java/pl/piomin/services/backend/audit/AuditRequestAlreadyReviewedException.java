package pl.piomin.services.backend.audit;

/**
 * Thrown when approving/rejecting a request whose {@code status} is no longer {@code PENDING}.
 * Mapped to {@code 409} by {@code GlobalExceptionHandler}.
 */
public class AuditRequestAlreadyReviewedException extends RuntimeException {

    private final Long id;
    private final String status;

    public AuditRequestAlreadyReviewedException(Long id, String status) {
        super("Audit request has already been reviewed: " + id);
        this.id = id;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public String getStatus() {
        return status;
    }
}
