package com.wdd.backend.exception;

/**
 * Thrown when a spread group name collides with another *live* group in the same brand. The
 * pending-duplicate case for CREATE is a distinct rule owned by the handler itself — see
 * {@link DuplicatePendingSpreadGroupCreateException}.
 */
public class SpreadGroupNameExistsException extends RuntimeException {

    private final Long brandId;
    private final String name;

    public SpreadGroupNameExistsException(Long brandId, String name) {
        super("Spread group name already exists for brand " + brandId + ": " + name);
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
