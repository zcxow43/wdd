package com.wdd.backend.dto;

import java.math.BigDecimal;

/**
 * Body for {@code PUT /api/currency-pairs/{id}}. Deliberately has no
 * {@code currencyPairDefinitionId}/{@code brandId} fields — both are
 * immutable after creation. Every field is optional; a field left out of
 * the request (i.e. {@code null} here) keeps its current value.
 */
public class CurrencyPairUpdateRequest {

    private String rateType;
    private BigDecimal rate;
    private Boolean active;

    public CurrencyPairUpdateRequest() {
    }

    public CurrencyPairUpdateRequest(String rateType, BigDecimal rate, Boolean active) {
        this.rateType = rateType;
        this.rate = rate;
        this.active = active;
    }

    public String getRateType() {
        return rateType;
    }

    public void setRateType(String rateType) {
        this.rateType = rateType;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public void setRate(BigDecimal rate) {
        this.rate = rate;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
