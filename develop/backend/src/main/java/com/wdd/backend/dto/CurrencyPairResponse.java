package com.wdd.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A single brand's currency pair — embedded in the fan-out list returned by
 * {@code POST /api/currency-pair-definitions}, and also the response shape
 * for the brand-scoped {@code /api/currency-pairs} CRUD API.
 * {@code baseCurrencyCode}/{@code quoteCurrencyCode} and
 * {@code spreadGroupId}/{@code spreadGroupName} are read-only enrichment
 * fields populated via the parent currency pair definition and (via a
 * {@code LEFT JOIN}) {@code spread_group} respectively; never writable
 * through this API.
 */
public class CurrencyPairResponse {

    private Long id;
    private Long currencyPairDefinitionId;
    private String baseCurrencyCode;
    private String quoteCurrencyCode;
    private Long brandId;
    private String brandCode;
    private String rateType;
    private BigDecimal rate;
    private Boolean active;
    private Long spreadGroupId;
    private String spreadGroupName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public CurrencyPairResponse() {
    }

    public CurrencyPairResponse(Long id, Long currencyPairDefinitionId, String baseCurrencyCode,
            String quoteCurrencyCode, Long brandId, String brandCode, String rateType, BigDecimal rate,
            Boolean active, Long spreadGroupId, String spreadGroupName, LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        this.id = id;
        this.currencyPairDefinitionId = currencyPairDefinitionId;
        this.baseCurrencyCode = baseCurrencyCode;
        this.quoteCurrencyCode = quoteCurrencyCode;
        this.brandId = brandId;
        this.brandCode = brandCode;
        this.rateType = rateType;
        this.rate = rate;
        this.active = active;
        this.spreadGroupId = spreadGroupId;
        this.spreadGroupName = spreadGroupName;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

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

    public String getRateType() {
        return rateType;
    }

    public void setRateType(String rateType) {
        this.rateType = rateType;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public void setRate(BigDecimal rate) {
        this.rate = rate;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
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
