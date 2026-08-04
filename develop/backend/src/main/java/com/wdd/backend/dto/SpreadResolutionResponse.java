package com.wdd.backend.dto;

import java.math.BigDecimal;

public class SpreadResolutionResponse {

    private Long currencyPairId;
    private Long brandId;
    private String source;
    private Long spreadGroupId;
    private String spreadGroupName;
    private BigDecimal depositSpread;
    private BigDecimal withdrawSpread;

    public SpreadResolutionResponse() {
    }

    public Long getCurrencyPairId() {
        return currencyPairId;
    }

    public void setCurrencyPairId(Long currencyPairId) {
        this.currencyPairId = currencyPairId;
    }

    public Long getBrandId() {
        return brandId;
    }

    public void setBrandId(Long brandId) {
        this.brandId = brandId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Long getSpreadGroupId() {
        return spreadGroupId;
    }

    public void setSpreadGroupId(Long spreadGroupId) {
        this.spreadGroupId = spreadGroupId;
    }

    public String getSpreadGroupName() {
        return spreadGroupName;
    }

    public void setSpreadGroupName(String spreadGroupName) {
        this.spreadGroupName = spreadGroupName;
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
}
