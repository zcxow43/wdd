package com.wdd.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response shape for {@code /api/spread-groups} list/create/update — does
 * not include the {@code members} list (see {@link SpreadGroupDetailResponse}
 * for that).
 */
public class SpreadGroupResponse {

    private Long id;
    private Long brandId;
    private String brandCode;
    private String name;
    private BigDecimal depositSpread;
    private BigDecimal withdrawalSpread;
    private Integer memberCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public SpreadGroupResponse() {
    }

    public SpreadGroupResponse(Long id, Long brandId, String brandCode, String name, BigDecimal depositSpread,
            BigDecimal withdrawalSpread, Integer memberCount, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.brandId = brandId;
        this.brandCode = brandCode;
        this.name = name;
        this.depositSpread = depositSpread;
        this.withdrawalSpread = withdrawalSpread;
        this.memberCount = memberCount;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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
