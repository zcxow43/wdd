package com.wdd.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Result of the join behind {@code GET /api/exchange-rates/latest} — one row
 * per {@code (currency_pair_definition, brand)} combination, enriched with
 * currency codes/precision/brand code and its most recent {@code exchange_rate}
 * row (if any). All exchange-rate fields are {@code null} for a combination
 * never synced.
 */
public class ExchangeRateLatest {

    private Long currencyPairDefinitionId;
    private String baseCurrencyCode;
    private String quoteCurrencyCode;
    private Integer precision;
    private Long brandId;
    private String brandCode;
    private BigDecimal rate;
    private BigDecimal depositRate;
    private BigDecimal withdrawalRate;
    private LocalDateTime rateMinute;
    private String source;

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

    public Integer getPrecision() {
        return precision;
    }

    public void setPrecision(Integer precision) {
        this.precision = precision;
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
}
