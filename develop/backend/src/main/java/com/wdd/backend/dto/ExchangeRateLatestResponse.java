package com.wdd.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ExchangeRateLatestResponse {

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

    public ExchangeRateLatestResponse() {
    }

    public ExchangeRateLatestResponse(Long currencyPairDefinitionId, String baseCurrencyCode,
            String quoteCurrencyCode, Integer precision, Long brandId, String brandCode, BigDecimal rate,
            BigDecimal depositRate, BigDecimal withdrawalRate, LocalDateTime rateMinute, String source) {
        this.currencyPairDefinitionId = currencyPairDefinitionId;
        this.baseCurrencyCode = baseCurrencyCode;
        this.quoteCurrencyCode = quoteCurrencyCode;
        this.precision = precision;
        this.brandId = brandId;
        this.brandCode = brandCode;
        this.rate = rate;
        this.depositRate = depositRate;
        this.withdrawalRate = withdrawalRate;
        this.rateMinute = rateMinute;
        this.source = source;
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
