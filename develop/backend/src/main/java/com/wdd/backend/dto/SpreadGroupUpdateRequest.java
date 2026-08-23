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
    private BigDecimal depositSpread;
    private BigDecimal withdrawalSpread;

    public SpreadGroupUpdateRequest() {
    }

    public SpreadGroupUpdateRequest(String name, BigDecimal depositSpread, BigDecimal withdrawalSpread) {
        this.name = name;
        this.depositSpread = depositSpread;
        this.withdrawalSpread = withdrawalSpread;
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

    public BigDecimal getWithdrawalSpread() {
        return withdrawalSpread;
    }

    public void setWithdrawalSpread(BigDecimal withdrawalSpread) {
        this.withdrawalSpread = withdrawalSpread;
    }
}
