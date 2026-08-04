package com.wdd.backend.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 1:1 with the {@code spread_group} table (specs/dba/spread-group.md) — a brand-scoped, freely
 * CRUD-able custom spread group; mutated only via the audit workflow (specs/backend/spread.md).
 */
public class SpreadGroup {

    private Long id;
    private Long brandId;
    private String name;
    private BigDecimal depositSpread;
    private BigDecimal withdrawSpread;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Enrichment field — populated only by the joined read queries, never persisted.
    private String brandCode;

    public SpreadGroup() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getBrandCode() {
        return brandCode;
    }

    public void setBrandCode(String brandCode) {
        this.brandCode = brandCode;
    }
}
