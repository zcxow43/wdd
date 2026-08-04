package com.wdd.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.wdd.backend.model.CurrencyPair;

public class CurrencyPairResponse {

    private Long id;
    private Long brandId;
    private String brandCode;
    private Long baseCurrencyId;
    private String baseCurrencyCode;
    private Long quoteCurrencyId;
    private String quoteCurrencyCode;
    private BigDecimal rate;
    private String rateType;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public CurrencyPairResponse() {
    }

    public static CurrencyPairResponse from(CurrencyPair pair) {
        CurrencyPairResponse response = new CurrencyPairResponse();
        response.setId(pair.getId());
        response.setBrandId(pair.getBrandId());
        response.setBrandCode(pair.getBrandCode());
        response.setBaseCurrencyId(pair.getBaseCurrencyId());
        response.setBaseCurrencyCode(pair.getBaseCurrencyCode());
        response.setQuoteCurrencyId(pair.getQuoteCurrencyId());
        response.setQuoteCurrencyCode(pair.getQuoteCurrencyCode());
        response.setRate(pair.getRate());
        response.setRateType(pair.getRateType());
        response.setActive(pair.getActive());
        response.setCreatedAt(pair.getCreatedAt());
        response.setUpdatedAt(pair.getUpdatedAt());
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

    public Long getBaseCurrencyId() {
        return baseCurrencyId;
    }

    public void setBaseCurrencyId(Long baseCurrencyId) {
        this.baseCurrencyId = baseCurrencyId;
    }

    public String getBaseCurrencyCode() {
        return baseCurrencyCode;
    }

    public void setBaseCurrencyCode(String baseCurrencyCode) {
        this.baseCurrencyCode = baseCurrencyCode;
    }

    public Long getQuoteCurrencyId() {
        return quoteCurrencyId;
    }

    public void setQuoteCurrencyId(Long quoteCurrencyId) {
        this.quoteCurrencyId = quoteCurrencyId;
    }

    public String getQuoteCurrencyCode() {
        return quoteCurrencyCode;
    }

    public void setQuoteCurrencyCode(String quoteCurrencyCode) {
        this.quoteCurrencyCode = quoteCurrencyCode;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public void setRate(BigDecimal rate) {
        this.rate = rate;
    }

    public String getRateType() {
        return rateType;
    }

    public void setRateType(String rateType) {
        this.rateType = rateType;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
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
