package com.wdd.backend.exception;

public class BrandNotFoundException extends RuntimeException {

    private final Long id;

    public BrandNotFoundException(Long id) {
        super("Brand not found: " + id);
        this.id = id;
    }

    public Long getId() {
        return id;
    }
}
