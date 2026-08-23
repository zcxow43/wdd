package com.wdd.backend.dto;

import java.math.BigDecimal;

/**
 * Body for {@code POST /api/spread-groups}. Spreads default to {@code 0}
 * when omitted.
 */
public class SpreadGroupCreateRequest {

    private Long brandId;
    private String name;
    private BigDecimal depositSpread;
    private BigDecimal withdrawalSpread;

    public SpreadGroupCreateRequest() {
    }

    public SpreadGroupCreateRequest(Long brandId, String name, BigDecimal depositSpread,
            BigDecimal withdrawalSpread) {
        this.brandId = brandId;
        this.name = name;
        this.depositSpread = depositSpread;
        this.withdrawalSpread = withdrawalSpread;
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

    public BigDecimal getWithdrawalSpread() {
        return withdrawalSpread;
    }

    public void setWithdrawalSpread(BigDecimal withdrawalSpread) {
        this.withdrawalSpread = withdrawalSpread;
    }
}
