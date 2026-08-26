package com.wdd.backend.dto;

import java.math.BigDecimal;

/**
 * Body for {@code PUT /api/brand-spreads/{brandId}}. Both fields are
 * required.
 */
public class BrandSpreadUpdateRequest {

    private BigDecimal depositSpreadPercent;
    private BigDecimal withdrawalSpreadPercent;

    public BrandSpreadUpdateRequest() {
    }

    public BrandSpreadUpdateRequest(BigDecimal depositSpreadPercent, BigDecimal withdrawalSpreadPercent) {
        this.depositSpreadPercent = depositSpreadPercent;
        this.withdrawalSpreadPercent = withdrawalSpreadPercent;
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
