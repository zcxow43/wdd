package com.wdd.backend.exception;

public class CurrencyPairDefinitionNotFoundException extends RuntimeException {

    public CurrencyPairDefinitionNotFoundException(Long id) {
        super("Currency pair definition not found: " + id);
    }
}
