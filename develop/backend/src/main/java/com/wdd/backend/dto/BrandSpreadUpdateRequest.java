package com.wdd.backend.dto;

import java.math.BigDecimal;

/**
 * Body for {@code PUT /api/brand-spreads/{brandId}}. Both fields are
 * required.
 */
public class BrandSpreadUpdateRequest {

    private BigDecimal depositSpread;
    private BigDecimal withdrawalSpread;

    public BrandSpreadUpdateRequest() {
    }

    public BrandSpreadUpdateRequest(BigDecimal depositSpread, BigDecimal withdrawalSpread) {
        this.depositSpread = depositSpread;
        this.withdrawalSpread = withdrawalSpread;
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
