package com.wdd.backend.dto;

/**
 * Optional request body for {@code DELETE /api/currency-pairs/{id}} — no fields are required
 * (a plain, body-less DELETE is also accepted), but a caller may supply {@code requestedBy} the
 * same way as {@code PUT} does, to be passed through to {@code AuditService.submit}.
 */
public class CurrencyPairDeleteRequest {

    private String requestedBy;

    public CurrencyPairDeleteRequest() {
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(String requestedBy) {
        this.requestedBy = requestedBy;
    }
}
