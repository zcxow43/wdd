package com.wdd.backend.dto;

/**
 * Body for {@code PUT /api/currencies/{id}}. Deliberately has no {@code code}
 * field — {@code code} is immutable after creation. If a client sends a
 * {@code code} property anyway, Spring's default Jackson configuration
 * (fail-on-unknown-properties = false) silently ignores it.
 */
public class CurrencyUpdateRequest {

    private String name;
    private String symbol;
    private Integer decimalPlaces;

    public CurrencyUpdateRequest() {
    }

    public CurrencyUpdateRequest(String name, String symbol, Integer decimalPlaces) {
        this.name = name;
        this.symbol = symbol;
        this.decimalPlaces = decimalPlaces;
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
