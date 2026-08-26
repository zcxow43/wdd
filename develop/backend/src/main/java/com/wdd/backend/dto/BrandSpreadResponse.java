package com.wdd.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response shape for {@code /api/brand-spreads} — a brand's default spread.
 * There is exactly one per brand, keyed by {@code brandId} rather than a
 * standalone row id.
 */
public class BrandSpreadResponse {

    private Long brandId;
    private String brandCode;
    private BigDecimal depositSpreadPercent;
    private BigDecimal withdrawalSpreadPercent;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public BrandSpreadResponse() {
    }

    public BrandSpreadResponse(Long brandId, String brandCode, BigDecimal depositSpreadPercent,
            BigDecimal withdrawalSpreadPercent, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.brandId = brandId;
        this.brandCode = brandCode;
        this.depositSpreadPercent = depositSpreadPercent;
        this.withdrawalSpreadPercent = withdrawalSpreadPercent;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getBrandId() {
        return brandId;
    }

    public void setBrandId(Long brandId) {
        this.brandId = brandId;
    }

    public String getBrandCode() {
        return brandCode;
    }

    public void setBrandCode(String brandCode) {
        this.brandCode = brandCode;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
