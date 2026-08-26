package com.wdd.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Persistence model mapped to the {@code exchange_rate} table. One row is a
 * per-(definition, brand) market-rate snapshot, truncated to the minute
 * ({@code rateMinute}): the plain provider {@code rate} (identical across
 * every brand for the same definition+minute) plus that brand's
 * then-currently-effective {@code depositRate}/{@code withdrawalRate},
 * frozen at sync time.
 */
public class ExchangeRate {

    private Long id;
    private Long currencyPairDefinitionId;
    private Long brandId;
    private BigDecimal rate;
    private BigDecimal depositRate;
    private BigDecimal withdrawalRate;
    private LocalDateTime rateMinute;
    private String source;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCurrencyPairDefinitionId() {
        return currencyPairDefinitionId;
    }

    public void setCurrencyPairDefinitionId(Long currencyPairDefinitionId) {
        this.currencyPairDefinitionId = currencyPairDefinitionId;
    }

    public Long getBrandId() {
        return brandId;
    }

    public void setBrandId(Long brandId) {
        this.brandId = brandId;
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

    public LocalDateTime getRateMinute() {
        return rateMinute;
    }

    public void setRateMinute(LocalDateTime rateMinute) {
        this.rateMinute = rateMinute;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
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
