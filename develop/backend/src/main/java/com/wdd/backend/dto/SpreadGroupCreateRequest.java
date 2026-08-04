package com.wdd.backend.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class SpreadGroupCreateRequest {

    @NotNull(message = "brandId is required")
    private Long brandId;

    @NotBlank(message = "name is required")
    @Size(max = 100, message = "name must be at most 100 characters")
    private String name;

    @NotNull(message = "depositSpread is required")
    @DecimalMin(value = "0.0", message = "depositSpread must be >= 0")
    private BigDecimal depositSpread;

    @NotNull(message = "withdrawSpread is required")
    @DecimalMin(value = "0.0", message = "withdrawSpread must be >= 0")
    private BigDecimal withdrawSpread;

    // Nullable — defaults to an empty list (no members) when omitted.
    private List<Long> currencyPairIds;

    // Optional — passed through to AuditService.submit as the audit request's requestedBy.
    private String requestedBy;

    public SpreadGroupCreateRequest() {
    }

    public Long getBrandId() {
        return brandId;
    }

    public void setBrandId(Long brandId) {
        this.brandId = brandId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getDepositSpread() {
        return depositSpread;
    }

    public void setDepositSpread(BigDecimal depositSpread) {
        this.depositSpread = depositSpread;
    }

    public BigDecimal getWithdrawSpread() {
        return withdrawSpread;
    }

    public void setWithdrawSpread(BigDecimal withdrawSpread) {
        this.withdrawSpread = withdrawSpread;
    }

    public List<Long> getCurrencyPairIds() {
        return currencyPairIds;
    }

    public void setCurrencyPairIds(List<Long> currencyPairIds) {
        this.currencyPairIds = currencyPairIds;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(String requestedBy) {
        this.requestedBy = requestedBy;
    }
}
