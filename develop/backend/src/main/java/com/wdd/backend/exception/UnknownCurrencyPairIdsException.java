package com.wdd.backend.exception;

import java.util.List;

/**
 * Thrown when a spread group member-assignment batch references
 * {@code currency_pair} ids that do not exist. None of the batch is
 * assigned.
 */
public class UnknownCurrencyPairIdsException extends RuntimeException {

    private final List<Long> currencyPairIds;

    public UnknownCurrencyPairIdsException(List<Long> currencyPairIds) {
        super("Unknown currency pair ids");
        this.currencyPairIds = currencyPairIds;
    }

    public List<Long> getCurrencyPairIds() {
        return currencyPairIds;
    }
}
