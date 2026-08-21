package com.wdd.backend.dto;

import java.time.LocalDateTime;

public class CurrencyResponse {

    private Long id;
    private String code;
    private String name;
    private String symbol;
    private Integer decimalPlaces;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public CurrencyResponse() {
    }

    public CurrencyResponse(Long id, String code, String name, String symbol, Integer decimalPlaces,
            LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.symbol = symbol;
        this.decimalPlaces = decimalPlaces;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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
