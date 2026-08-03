package pl.piomin.services.backend.audit;

/**
 * Thrown when a lookup by id finds no matching {@code audit_request} row. Mapped to {@code 404}
 * by {@code GlobalExceptionHandler}.
 */
public class AuditRequestNotFoundException extends RuntimeException {

    private final Long id;

    public AuditRequestNotFoundException(Long id) {
        super("Audit request not found: " + id);
        this.id = id;
    }

    public Long getId() {
        return id;
    }
}
