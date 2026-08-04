package com.wdd.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class CurrencyPairDefinitionCreateRequest {

    @NotNull(message = "baseCurrencyId is required")
    private Long baseCurrencyId;

    @NotNull(message = "quoteCurrencyId is required")
    private Long quoteCurrencyId;

    @NotNull(message = "forwardPrecision is required")
    @Min(value = 0, message = "forwardPrecision must be between 0 and 8")
    @Max(value = 8, message = "forwardPrecision must be between 0 and 8")
    private Integer forwardPrecision;

    @NotNull(message = "reversePrecision is required")
    @Min(value = 0, message = "reversePrecision must be between 0 and 8")
    @Max(value = 8, message = "reversePrecision must be between 0 and 8")
    private Integer reversePrecision;

    public CurrencyPairDefinitionCreateRequest() {
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

    public Integer getForwardPrecision() {
        return forwardPrecision;
    }

    public void setForwardPrecision(Integer forwardPrecision) {
        this.forwardPrecision = forwardPrecision;
    }

    public Integer getReversePrecision() {
        return reversePrecision;
    }

    public void setReversePrecision(Integer reversePrecision) {
        this.reversePrecision = reversePrecision;
    }
}
