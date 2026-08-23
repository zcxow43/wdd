package com.wdd.backend.service;

import com.wdd.backend.dto.AuditRequest;

/**
 * The contract each audited entity implements and registers with the audit
 * module. This module knows nothing about any concrete entity — it only
 * calls these two methods, resolved by {@link #entityType()} through
 * {@link AuditHandlerRegistry}. A Spring {@code @Component} implementing
 * this interface anywhere on the classpath is picked up automatically; the
 * audit module itself never changes when a new audited entity is added.
 */
public interface AuditHandler {

    /** The {@code entityType} string this handler is registered for. */
    String entityType();

    /**
     * Re-validates the request against current data. Implementations throw
     * {@link com.wdd.backend.exception.AuditHandlerException} if the change
     * is no longer legal (the target was deleted, a value now collides,
     * etc.) — any other exception is treated as an unexpected server error.
     */
    void validate(AuditRequest request);

    /**
     * Performs the real change described by the request. Called only after
     * {@link #validate(AuditRequest)} succeeds, in the same transaction.
     */
    void apply(AuditRequest request);
}
