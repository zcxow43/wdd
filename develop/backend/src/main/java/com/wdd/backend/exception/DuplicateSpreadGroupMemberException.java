package com.wdd.backend.exception;

/**
 * Thrown when the same {@code currencyPairId} appears more than once in a spread group's
 * proposed {@code currencyPairIds} payload.
 */
public class DuplicateSpreadGroupMemberException extends RuntimeException {

    private final Long currencyPairId;

    public DuplicateSpreadGroupMemberException(Long currencyPairId) {
        super("Duplicate currency pair id in currencyPairIds: " + currencyPairId);
        this.currencyPairId = currencyPairId;
    }

    public Long getCurrencyPairId() {
        return currencyPairId;
    }
}
