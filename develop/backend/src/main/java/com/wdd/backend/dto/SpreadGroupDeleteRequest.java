package com.wdd.backend.dto;

/**
 * Optional request body for {@code DELETE /api/spread-groups/{id}} — mirrors
 * {@code CurrencyPairDeleteRequest}: no fields are required (a plain, body-less DELETE is also
 * accepted), but a caller may supply {@code requestedBy}.
 */
public class SpreadGroupDeleteRequest {

    private String requestedBy;

    public SpreadGroupDeleteRequest() {
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(String requestedBy) {
        this.requestedBy = requestedBy;
    }
}
