package com.wdd.backend.exception;

public class CurrencyCodeConflictException extends RuntimeException {

    public CurrencyCodeConflictException(String code) {
        super("Currency code already in use: " + code);
    }
}
