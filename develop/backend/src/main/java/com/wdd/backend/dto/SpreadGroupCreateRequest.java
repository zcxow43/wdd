package com.wdd.backend.dto;

import java.math.BigDecimal;

/**
 * Body for {@code POST /api/spread-groups}. Spreads default to {@code 0}
 * when omitted.
 */
public class SpreadGroupCreateRequest {

    private Long brandId;
    private String name;
    private BigDecimal depositSpreadPercent;
    private BigDecimal withdrawalSpreadPercent;

    public SpreadGroupCreateRequest() {
    }

    public SpreadGroupCreateRequest(Long brandId, String name, BigDecimal depositSpreadPercent,
            BigDecimal withdrawalSpreadPercent) {
        this.brandId = brandId;
        this.name = name;
        this.depositSpreadPercent = depositSpreadPercent;
        this.withdrawalSpreadPercent = withdrawalSpreadPercent;
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
