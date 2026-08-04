package com.wdd.backend.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;

public class CurrencyPairUpdateRequest {

    // All fields optional — partial update. Only fields actually present (non-null)
    // in the request are applied/re-validated by the service layer.
    private Long brandId;

    private Long baseCurrencyId;

    private Long quoteCurrencyId;

    @DecimalMin(value = "0.0", inclusive = false, message = "rate must be greater than 0")
    private BigDecimal rate;

    @Pattern(regexp = "^(MANUAL|AUTO)$", message = "rateType must be MANUAL or AUTO")
    private String rateType;

    private Boolean active;

    // Optional — passed through to AuditService.submit as the audit request's requestedBy.
    private String requestedBy;

    public CurrencyPairUpdateRequest() {
    }

    public Long getBrandId() {
        return brandId;
    }

    public void setBrandId(Long brandId) {
        this.brandId = brandId;
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

    public BigDecimal getRate() {
        return rate;
    }

    public void setRate(BigDecimal rate) {
        this.rate = rate;
    }

    public String getRateType() {
        return rateType;
    }

    public void setRateType(String rateType) {
        this.rateType = rateType;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(String requestedBy) {
        this.requestedBy = requestedBy;
    }
}
