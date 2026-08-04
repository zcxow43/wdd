package com.wdd.backend.exception;

public class CurrencyPairNotFoundException extends RuntimeException {

    private final Long id;

    public CurrencyPairNotFoundException(Long id) {
        super("Currency pair not found: " + id);
        this.id = id;
    }

    public Long getId() {
        return id;
    }
}
