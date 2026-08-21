package com.wdd.backend.exception;

public class CurrencyPairNotFoundException extends RuntimeException {

    public CurrencyPairNotFoundException(Long id) {
        super("Currency pair not found: " + id);
    }
}
