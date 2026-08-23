package com.wdd.backend.exception;

public class SpreadGroupNotFoundException extends RuntimeException {

    public SpreadGroupNotFoundException(Long id) {
        super("Spread group not found: " + id);
    }
}
