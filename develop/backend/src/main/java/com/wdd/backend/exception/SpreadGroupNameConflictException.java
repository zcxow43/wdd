package com.wdd.backend.exception;

/**
 * Thrown when a spread group's {@code (brandId, name)} would collide with
 * an existing group of the same brand, on create or rename.
 */
public class SpreadGroupNameConflictException extends RuntimeException {

    public SpreadGroupNameConflictException() {
        super("Spread group name already exists for this brand");
    }
}
