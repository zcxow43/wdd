package com.wdd.backend.dto;

public class CurrencyPairDefinitionCreateRequest {

    private Long baseCurrencyId;
    private Long quoteCurrencyId;
    private Integer precision;

    public CurrencyPairDefinitionCreateRequest() {
    }

    public CurrencyPairDefinitionCreateRequest(Long baseCurrencyId, Long quoteCurrencyId, Integer precision) {
        this.baseCurrencyId = baseCurrencyId;
        this.quoteCurrencyId = quoteCurrencyId;
        this.precision = precision;
    }

    public Long getBaseCurrencyId() {
        return baseCurrencyId;
    }

    public void setBaseCurrencyId(Long baseCurrencyId) {
        this.baseCurrencyId = baseCurrencyId;
    }

    public Long getQuoteCurrencyId() {
        return quoteCurrencyId;
    }

    public void setQuoteCurrencyId(Long quoteCurrencyId) {
        this.quoteCurrencyId = quoteCurrencyId;
    }

    public Integer getPrecision() {
        return precision;
    }

    public void setPrecision(Integer precision) {
        this.precision = precision;
    }
}
