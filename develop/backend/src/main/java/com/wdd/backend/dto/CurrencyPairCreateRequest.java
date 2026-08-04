package com.wdd.backend.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public class CurrencyPairCreateRequest {

    @NotNull(message = "brandId is required")
    private Long brandId;

    @NotNull(message = "baseCurrencyId is required")
    private Long baseCurrencyId;

    @NotNull(message = "quoteCurrencyId is required")
    private Long quoteCurrencyId;

    // No @NotNull: rate is conditionally required based on rateType, enforced in the
    // service layer (MANUAL requires rate > 0; AUTO forces it to null regardless).
    @DecimalMin(value = "0.0", inclusive = false, message = "rate must be greater than 0")
    private BigDecimal rate;

    @NotBlank(message = "rateType is required")
    @Pattern(regexp = "^(MANUAL|AUTO)$", message = "rateType must be MANUAL or AUTO")
    private String rateType;

    private Boolean active;

    public CurrencyPairCreateRequest() {
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
}
