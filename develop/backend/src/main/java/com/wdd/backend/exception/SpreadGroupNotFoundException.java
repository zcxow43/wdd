package com.wdd.backend.exception;

public class SpreadGroupNotFoundException extends RuntimeException {

    private final Long id;

    public SpreadGroupNotFoundException(Long id) {
        super("Spread group not found: " + id);
        this.id = id;
    }

    public Long getId() {
        return id;
    }
}
