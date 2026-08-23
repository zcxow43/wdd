package com.wdd.backend.dto;

/**
 * Request body for approve/reject/cancel. {@code comment} is required and
 * validated (non-blank, 1-500 chars) only by {@code reject}; it is optional
 * for approve/cancel.
 */
public class AuditActionRequest {

    private String comment;

    public AuditActionRequest() {
    }

    public AuditActionRequest(String comment) {
        this.comment = comment;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
