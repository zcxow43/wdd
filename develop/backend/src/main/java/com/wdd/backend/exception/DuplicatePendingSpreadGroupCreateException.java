package com.wdd.backend.exception;

/**
 * Thrown by {@code SpreadGroupAuditHandler.validate} when a PENDING SPREAD_GROUP/CREATE request
 * already exists for the same (brandId, name) combination — the natural-key dedup check for
 * CREATE, since entityId doesn't exist yet for the generic {@code AuditService} dedup check
 * (which is keyed on entityId) to catch this case (specs/backend/spread.md).
 */
public class DuplicatePendingSpreadGroupCreateException extends RuntimeException {

    private final Long brandId;
    private final String name;

    public DuplicatePendingSpreadGroupCreateException(Long brandId, String name) {
        super("A pending create request already exists for brand " + brandId + "/name " + name);
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
