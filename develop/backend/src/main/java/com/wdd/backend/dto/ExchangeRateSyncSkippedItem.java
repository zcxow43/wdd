package com.wdd.backend.dto;

public class ExchangeRateSyncSkippedItem {

    private Long currencyPairDefinitionId;
    private String baseCurrencyCode;
    private String quoteCurrencyCode;
    private String reason;

    public ExchangeRateSyncSkippedItem() {
    }

    public ExchangeRateSyncSkippedItem(Long currencyPairDefinitionId, String baseCurrencyCode,
            String quoteCurrencyCode, String reason) {
        this.currencyPairDefinitionId = currencyPairDefinitionId;
        this.baseCurrencyCode = baseCurrencyCode;
        this.quoteCurrencyCode = quoteCurrencyCode;
        this.reason = reason;
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

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
