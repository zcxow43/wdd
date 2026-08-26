package com.wdd.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Persistence model mapped to the {@code spread_group} table — a brand's
 * named group spread (群組點差). {@code brandCode} is a read-only
 * enrichment field populated via a join against {@code brand};
 * {@code memberCount} is populated via a grouped {@code COUNT} over
 * {@code currency_pair}.
 */
public class SpreadGroup {

    private Long id;
    private Long brandId;
    private String brandCode;
    private String name;
    private BigDecimal depositSpreadPercent;
    private BigDecimal withdrawalSpreadPercent;
    private Integer memberCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

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

    public String getBrandCode() {
        return brandCode;
    }

    public void setBrandCode(String brandCode) {
        this.brandCode = brandCode;
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

    public Integer getMemberCount() {
        return memberCount;
    }

    public void setMemberCount(Integer memberCount) {
        this.memberCount = memberCount;
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
