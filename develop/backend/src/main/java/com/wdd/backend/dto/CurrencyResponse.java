package com.wdd.backend.dto;

import java.time.LocalDateTime;

import com.wdd.backend.model.Currency;

public class CurrencyResponse {

    private Long id;
    private String code;
    private String name;
    private String nameZh;
    private String symbol;
    private Integer decimalPlaces;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public CurrencyResponse() {
    }

    public static CurrencyResponse from(Currency currency) {
        CurrencyResponse response = new CurrencyResponse();
        response.setId(currency.getId());
        response.setCode(currency.getCode());
        response.setName(currency.getName());
        response.setNameZh(currency.getNameZh());
        response.setSymbol(currency.getSymbol());
        response.setDecimalPlaces(currency.getDecimalPlaces());
        response.setCreatedAt(currency.getCreatedAt());
        response.setUpdatedAt(currency.getUpdatedAt());
        return response;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNameZh() {
        return nameZh;
    }

    public void setNameZh(String nameZh) {
        this.nameZh = nameZh;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public Integer getDecimalPlaces() {
        return decimalPlaces;
    }

    public void setDecimalPlaces(Integer decimalPlaces) {
        this.decimalPlaces = decimalPlaces;
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
