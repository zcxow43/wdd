package com.wdd.backend.exception;

public class CurrencyNotFoundException extends RuntimeException {

    public CurrencyNotFoundException(Long id) {
        super("Currency not found: " + id);
    }
}
