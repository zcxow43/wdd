package pl.piomin.services.backend.dto;

/**
 * Optional request body for {@code DELETE /api/currency-pairs/{id}}. No field
 * is required - a caller may submit an empty/no body, or supply
 * {@code requestedBy} for audit tracking, the same way create/update do.
 */
public class CurrencyPairDeleteRequest {

    private String requestedBy;

    public String getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(String requestedBy) {
        this.requestedBy = requestedBy;
    }
}
