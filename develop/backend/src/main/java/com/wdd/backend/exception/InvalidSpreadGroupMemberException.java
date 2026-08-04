package com.wdd.backend.exception;

/**
 * Thrown when a {@code currencyPairId} proposed as a spread group member belongs to a different
 * brand than the group's own {@code brandId}.
 */
public class InvalidSpreadGroupMemberException extends RuntimeException {

    private final Long currencyPairId;
    private final Long brandId;

    public InvalidSpreadGroupMemberException(Long currencyPairId, Long brandId) {
        super("Currency pair " + currencyPairId + " does not belong to brand " + brandId);
        this.currencyPairId = currencyPairId;
        this.brandId = brandId;
    }

    public Long getCurrencyPairId() {
        return currencyPairId;
    }

    public Long getBrandId() {
        return brandId;
    }
}
