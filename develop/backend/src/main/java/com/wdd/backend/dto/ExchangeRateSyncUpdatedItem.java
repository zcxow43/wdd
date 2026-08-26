package com.wdd.backend.dto;

import java.math.BigDecimal;

public class ExchangeRateSyncUpdatedItem {

    private Long currencyPairDefinitionId;
    private String baseCurrencyCode;
    private String quoteCurrencyCode;
    private Long brandId;
    private String brandCode;
    private BigDecimal rate;
    private BigDecimal depositRate;
    private BigDecimal withdrawalRate;

    public ExchangeRateSyncUpdatedItem() {
    }

    public ExchangeRateSyncUpdatedItem(Long currencyPairDefinitionId, String baseCurrencyCode,
            String quoteCurrencyCode, Long brandId, String brandCode, BigDecimal rate, BigDecimal depositRate,
            BigDecimal withdrawalRate) {
        this.currencyPairDefinitionId = currencyPairDefinitionId;
        this.baseCurrencyCode = baseCurrencyCode;
        this.quoteCurrencyCode = quoteCurrencyCode;
        this.brandId = brandId;
        this.brandCode = brandCode;
        this.rate = rate;
        this.depositRate = depositRate;
        this.withdrawalRate = withdrawalRate;
    }

    public Long getCurrencyPairDefinitionId() {
        return currencyPairDefinitionId;
    }

    public void setCurrencyPairDefinitionId(Long currencyPairDefinitionId) {
        this.currencyPairDefinitionId = currencyPairDefinitionId;
    }

    public String getBaseCurrencyCode() {
        return baseCurrencyCode;
    }

    public void setBaseCurrencyCode(String baseCurrencyCode) {
        this.baseCurrencyCode = baseCurrencyCode;
    }

    public String getQuoteCurrencyCode() {
        return quoteCurrencyCode;
    }

    public void setQuoteCurrencyCode(String quoteCurrencyCode) {
        this.quoteCurrencyCode = quoteCurrencyCode;
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

    public BigDecimal getRate() {
        return rate;
    }

    public void setRate(BigDecimal rate) {
        this.rate = rate;
    }

    public BigDecimal getDepositRate() {
        return depositRate;
    }

    public void setDepositRate(BigDecimal depositRate) {
        this.depositRate = depositRate;
    }

    public BigDecimal getWithdrawalRate() {
        return withdrawalRate;
    }

    public void setWithdrawalRate(BigDecimal withdrawalRate) {
        this.withdrawalRate = withdrawalRate;
    }
}
