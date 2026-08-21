package com.wdd.backend.dto;

public class CurrencyCreateRequest {

    private String code;
    private String name;
    private String symbol;
    private Integer decimalPlaces;

    public CurrencyCreateRequest() {
    }

    public CurrencyCreateRequest(String code, String name, String symbol, Integer decimalPlaces) {
        this.code = code;
        this.name = name;
        this.symbol = symbol;
        this.decimalPlaces = decimalPlaces;
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
}
