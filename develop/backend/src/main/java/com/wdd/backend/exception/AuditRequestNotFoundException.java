package com.wdd.backend.exception;

public class AuditRequestNotFoundException extends RuntimeException {

    public AuditRequestNotFoundException(Long id) {
        super("Audit request not found: " + id);
    }
}
