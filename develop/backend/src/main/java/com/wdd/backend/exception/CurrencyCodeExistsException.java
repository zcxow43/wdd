package com.wdd.backend.exception;

public class CurrencyCodeExistsException extends RuntimeException {

    private final String code;

    public CurrencyCodeExistsException(String code) {
        super("Currency code already exists: " + code);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
