package com.wdd.backend.dto;

import java.math.BigDecimal;

/**
 * Body for {@code PUT /api/spread-groups/{id}}. Deliberately has no
 * {@code brandId} field — it is immutable after creation and ignored if
 * sent. Every field is optional; a field left out of the request (i.e.
 * {@code null} here) keeps its current value.
 */
public class SpreadGroupUpdateRequest {

    private String name;
    private BigDecimal depositSpreadPercent;
    private BigDecimal withdrawalSpreadPercent;

    public SpreadGroupUpdateRequest() {
    }

    public SpreadGroupUpdateRequest(String name, BigDecimal depositSpreadPercent, BigDecimal withdrawalSpreadPercent) {
        this.name = name;
        this.depositSpreadPercent = depositSpreadPercent;
        this.withdrawalSpreadPercent = withdrawalSpreadPercent;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getDepositSpreadPercent() {
        return depositSpreadPercent;
    }

    public void setDepositSpreadPercent(BigDecimal depositSpreadPercent) {
        this.depositSpreadPercent = depositSpreadPercent;
    }

    public BigDecimal getWithdrawalSpreadPercent() {
        return withdrawalSpreadPercent;
    }

    public void setWithdrawalSpreadPercent(BigDecimal withdrawalSpreadPercent) {
        this.withdrawalSpreadPercent = withdrawalSpreadPercent;
    }
}
