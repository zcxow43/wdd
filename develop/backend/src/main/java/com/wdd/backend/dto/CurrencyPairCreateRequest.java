package com.wdd.backend.dto;

import java.math.BigDecimal;

/**
 * Body for {@code POST /api/currency-pairs}. {@code rateType} defaults to
 * {@code AUTO} and {@code active} defaults to {@code false} when omitted.
 */
public class CurrencyPairCreateRequest {

    private Long currencyPairDefinitionId;
    private Long brandId;
    private String rateType;
    private BigDecimal rate;
    private Boolean active;

    public CurrencyPairCreateRequest() {
    }

    public CurrencyPairCreateRequest(Long currencyPairDefinitionId, Long brandId, String rateType, BigDecimal rate,
            Boolean active) {
        this.currencyPairDefinitionId = currencyPairDefinitionId;
        this.brandId = brandId;
        this.rateType = rateType;
        this.rate = rate;
        this.active = active;
    }

    public Long getCurrencyPairDefinitionId() {
        return currencyPairDefinitionId;
    }

    public void setCurrencyPairDefinitionId(Long currencyPairDefinitionId) {
        this.currencyPairDefinitionId = currencyPairDefinitionId;
    }

    public Long getBrandId() {
        return brandId;
    }

    public void setBrandId(Long brandId) {
        this.brandId = brandId;
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
