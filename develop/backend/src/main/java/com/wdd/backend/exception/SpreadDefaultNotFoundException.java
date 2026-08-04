package com.wdd.backend.exception;

public class SpreadDefaultNotFoundException extends RuntimeException {

    private final Long id;

    public SpreadDefaultNotFoundException(Long id) {
        super("Spread default not found: " + id);
        this.id = id;
    }

    public Long getId() {
        return id;
    }
}
