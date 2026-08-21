package com.wdd.backend.exception;

public class CurrencyPairDefinitionConflictException extends RuntimeException {

    public CurrencyPairDefinitionConflictException(Long baseCurrencyId, Long quoteCurrencyId) {
        super("Currency pair definition already exists for base currency " + baseCurrencyId
                + " and quote currency " + quoteCurrencyId);
    }
}
