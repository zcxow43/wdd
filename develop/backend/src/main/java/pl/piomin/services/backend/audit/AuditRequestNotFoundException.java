package pl.piomin.services.backend.audit;

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
