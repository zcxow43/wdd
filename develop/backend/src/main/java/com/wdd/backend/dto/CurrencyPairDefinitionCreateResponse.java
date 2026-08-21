package com.wdd.backend.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response body for {@code POST /api/currency-pair-definitions}: the newly
 * created definition plus the brand currency pairs it fanned out to.
 */
public class CurrencyPairDefinitionCreateResponse {

    private Long id;
    private Long baseCurrencyId;
    private String baseCurrencyCode;
    private Long quoteCurrencyId;
    private String quoteCurrencyCode;
    private Integer precision;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<CurrencyPairResponse> currencyPairs;

    public CurrencyPairDefinitionCreateResponse() {
    }

    public CurrencyPairDefinitionCreateResponse(Long id, Long baseCurrencyId, String baseCurrencyCode,
            Long quoteCurrencyId, String quoteCurrencyCode, Integer precision,
            LocalDateTime createdAt, LocalDateTime updatedAt, List<CurrencyPairResponse> currencyPairs) {
        this.id = id;
        this.baseCurrencyId = baseCurrencyId;
        this.baseCurrencyCode = baseCurrencyCode;
        this.quoteCurrencyId = quoteCurrencyId;
        this.quoteCurrencyCode = quoteCurrencyCode;
        this.precision = precision;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.currencyPairs = currencyPairs;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Integer getPrecision() {
        return precision;
    }

    public void setPrecision(Integer precision) {
        this.precision = precision;
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

    public List<CurrencyPairResponse> getCurrencyPairs() {
        return currencyPairs;
    }

    public void setCurrencyPairs(List<CurrencyPairResponse> currencyPairs) {
        this.currencyPairs = currencyPairs;
    }
}
