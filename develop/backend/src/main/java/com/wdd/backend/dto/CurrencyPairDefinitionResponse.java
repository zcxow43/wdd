package com.wdd.backend.dto;

import java.time.LocalDateTime;

import com.wdd.backend.model.CurrencyPairDefinition;

public class CurrencyPairDefinitionResponse {

    private Long id;
    private Long baseCurrencyId;
    private String baseCurrencyCode;
    private Long quoteCurrencyId;
    private String quoteCurrencyCode;
    private Integer forwardPrecision;
    private Integer reversePrecision;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public CurrencyPairDefinitionResponse() {
    }

    public static CurrencyPairDefinitionResponse from(CurrencyPairDefinition definition) {
        CurrencyPairDefinitionResponse response = new CurrencyPairDefinitionResponse();
        response.setId(definition.getId());
        response.setBaseCurrencyId(definition.getBaseCurrencyId());
        response.setBaseCurrencyCode(definition.getBaseCurrencyCode());
        response.setQuoteCurrencyId(definition.getQuoteCurrencyId());
        response.setQuoteCurrencyCode(definition.getQuoteCurrencyCode());
        response.setForwardPrecision(definition.getForwardPrecision());
        response.setReversePrecision(definition.getReversePrecision());
        response.setCreatedAt(definition.getCreatedAt());
        response.setUpdatedAt(definition.getUpdatedAt());
        return response;
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

    public Integer getForwardPrecision() {
        return forwardPrecision;
    }

    public void setForwardPrecision(Integer forwardPrecision) {
        this.forwardPrecision = forwardPrecision;
    }

    public Integer getReversePrecision() {
        return reversePrecision;
    }

    public void setReversePrecision(Integer reversePrecision) {
        this.reversePrecision = reversePrecision;
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
