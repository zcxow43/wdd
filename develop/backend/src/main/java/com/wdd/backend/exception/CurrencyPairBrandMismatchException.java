package com.wdd.backend.exception;

import java.util.List;

/**
 * Thrown when a spread group member-assignment batch includes a currency
 * pair whose {@code brandId} does not match the group's brand. None of the
 * batch is assigned.
 */
public class CurrencyPairBrandMismatchException extends RuntimeException {

    private final List<Long> currencyPairIds;

    public CurrencyPairBrandMismatchException(List<Long> currencyPairIds) {
        super("Currency pair belongs to a different brand");
        this.currencyPairIds = currencyPairIds;
    }

    public List<Long> getCurrencyPairIds() {
        return currencyPairIds;
    }
}
