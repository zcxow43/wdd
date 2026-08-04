package com.wdd.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.wdd.backend.model.SpreadDefault;

public class SpreadDefaultResponse {

    private Long id;
    private Long brandId;
    private String brandCode;
    private BigDecimal depositSpread;
    private BigDecimal withdrawSpread;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public SpreadDefaultResponse() {
    }

    public static SpreadDefaultResponse from(SpreadDefault spreadDefault) {
        SpreadDefaultResponse response = new SpreadDefaultResponse();
        response.setId(spreadDefault.getId());
        response.setBrandId(spreadDefault.getBrandId());
        response.setBrandCode(spreadDefault.getBrandCode());
        response.setDepositSpread(spreadDefault.getDepositSpread());
        response.setWithdrawSpread(spreadDefault.getWithdrawSpread());
        response.setCreatedAt(spreadDefault.getCreatedAt());
        response.setUpdatedAt(spreadDefault.getUpdatedAt());
        return response;
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
}
