package pl.piomin.services.backend.exception;

/**
 * Thrown by {@code SpreadGroupAuditHandler} when a CREATE is submitted for a
 * (brandId, name) combination that already has a PENDING CREATE audit request.
 * This natural-key dedup rule is the handler's own responsibility
 * (specs/backend/audit.md) since there is no entityId yet for the generic
 * audit module to dedup on.
 */
public class DuplicatePendingSpreadGroupCreateException extends RuntimeException {

    private final Long brandId;
    private final String name;

    public DuplicatePendingSpreadGroupCreateException(Long brandId, String name) {
        super("A pending create request already exists for this brand/name combination");
        this.brandId = brandId;
        this.name = name;
    }

    public Long getBrandId() {
        return brandId;
    }

    public String getName() {
        return name;
    }
}
