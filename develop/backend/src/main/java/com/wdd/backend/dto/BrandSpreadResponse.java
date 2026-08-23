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
    private BigDecimal depositSpread;
    private BigDecimal withdrawalSpread;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public BrandSpreadResponse() {
    }

    public BrandSpreadResponse(Long brandId, String brandCode, BigDecimal depositSpread,
            BigDecimal withdrawalSpread, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.brandId = brandId;
        this.brandCode = brandCode;
        this.depositSpread = depositSpread;
        this.withdrawalSpread = withdrawalSpread;
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
