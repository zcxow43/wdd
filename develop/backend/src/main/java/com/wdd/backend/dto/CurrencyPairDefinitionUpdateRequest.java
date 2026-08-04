package com.wdd.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Precision-only update — {@code baseCurrencyId}/{@code quoteCurrencyId} are immutable after
 * creation and intentionally not accepted by this DTO at all.
 */
public class CurrencyPairDefinitionUpdateRequest {

    @NotNull(message = "forwardPrecision is required")
    @Min(value = 0, message = "forwardPrecision must be between 0 and 8")
    @Max(value = 8, message = "forwardPrecision must be between 0 and 8")
    private Integer forwardPrecision;

    @NotNull(message = "reversePrecision is required")
    @Min(value = 0, message = "reversePrecision must be between 0 and 8")
    @Max(value = 8, message = "reversePrecision must be between 0 and 8")
    private Integer reversePrecision;

    public CurrencyPairDefinitionUpdateRequest() {
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
