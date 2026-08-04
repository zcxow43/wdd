package com.wdd.backend.audit;

public class AuditRequestAlreadyReviewedException extends RuntimeException {

    private final Long id;
    private final String status;

    public AuditRequestAlreadyReviewedException(Long id, String status) {
        super("Audit request has already been reviewed: " + id + " (status=" + status + ")");
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
