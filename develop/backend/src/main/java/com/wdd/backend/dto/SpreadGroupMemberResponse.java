package com.wdd.backend.dto;

/**
 * Read-only projection of a brand currency pair that is a member of a
 * {@link SpreadGroupDetailResponse}. Embedded in {@code GET
 * /api/spread-groups/{id}} and the member-assignment endpoint's response.
 */
public class SpreadGroupMemberResponse {

    private Long currencyPairId;
    private Long currencyPairDefinitionId;
    private String baseCurrencyCode;
    private String quoteCurrencyCode;
    private Boolean active;

    public SpreadGroupMemberResponse() {
    }

    public SpreadGroupMemberResponse(Long currencyPairId, Long currencyPairDefinitionId, String baseCurrencyCode,
            String quoteCurrencyCode, Boolean active) {
        this.currencyPairId = currencyPairId;
        this.currencyPairDefinitionId = currencyPairDefinitionId;
        this.baseCurrencyCode = baseCurrencyCode;
        this.quoteCurrencyCode = quoteCurrencyCode;
        this.active = active;
    }

    public Long getCurrencyPairId() {
        return currencyPairId;
    }

    public void setCurrencyPairId(Long currencyPairId) {
        this.currencyPairId = currencyPairId;
    }

    public Long getCurrencyPairDefinitionId() {
        return currencyPairDefinitionId;
    }

    public void setCurrencyPairDefinitionId(Long currencyPairDefinitionId) {
        this.currencyPairDefinitionId = currencyPairDefinitionId;
    }

    public String getBaseCurrencyCode() {
        return baseCurrencyCode;
    }

    public void setBaseCurrencyCode(String baseCurrencyCode) {
        this.baseCurrencyCode = baseCurrencyCode;
    }

    public String getQuoteCurrencyCode() {
        return quoteCurrencyCode;
    }

    public void setQuoteCurrencyCode(String quoteCurrencyCode) {
        this.quoteCurrencyCode = quoteCurrencyCode;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
