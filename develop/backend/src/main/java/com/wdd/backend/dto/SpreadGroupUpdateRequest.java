package com.wdd.backend.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

/**
 * All fields optional — partial update, same convention as {@code CurrencyPairUpdateRequest}.
 * {@code currencyPairIds} carries special "omitted means unchanged" semantics: when absent
 * (null), {@code SpreadGroupAuditHandler.validate} freezes the group's current live membership
 * into the persisted snapshot instead (specs/backend/spread.md).
 */
public class SpreadGroupUpdateRequest {

    @Size(max = 100, message = "name must be at most 100 characters")
    private String name;

    @DecimalMin(value = "0.0", message = "depositSpread must be >= 0")
    private BigDecimal depositSpread;

    @DecimalMin(value = "0.0", message = "withdrawSpread must be >= 0")
    private BigDecimal withdrawSpread;

    private List<Long> currencyPairIds;

    // Optional — passed through to AuditService.submit as the audit request's requestedBy.
    private String requestedBy;

    public SpreadGroupUpdateRequest() {
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
