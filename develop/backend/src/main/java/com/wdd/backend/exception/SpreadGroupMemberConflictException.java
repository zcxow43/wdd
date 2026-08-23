package com.wdd.backend.exception;

import java.util.List;
import java.util.Map;

/**
 * Thrown when a spread group member-assignment batch includes a currency
 * pair that already belongs to a <em>different</em> spread group. None of
 * the batch is assigned — reassignment requires removing the pair from its
 * current group first.
 */
public class SpreadGroupMemberConflictException extends RuntimeException {

    private final List<Map<String, Object>> conflicts;

    public SpreadGroupMemberConflictException(List<Map<String, Object>> conflicts) {
        super("Currency pair already belongs to another spread group");
        this.conflicts = conflicts;
    }

    public List<Map<String, Object>> getConflicts() {
        return conflicts;
    }
}
