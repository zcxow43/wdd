package com.wdd.backend.exception;

public class CurrencyPairConflictException extends RuntimeException {

    public CurrencyPairConflictException(Long currencyPairDefinitionId, Long brandId) {
        super("Currency pair already exists for definition " + currencyPairDefinitionId
                + " and brand " + brandId);
    }
}
