package com.wdd.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class CurrencyCreateRequest {

    @NotBlank(message = "code is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "code must be exactly 3 uppercase letters")
    private String code;

    @NotBlank(message = "name is required")
    @Size(max = 100, message = "name must be at most 100 characters")
    private String name;

    @Size(max = 100, message = "nameZh must be at most 100 characters")
    private String nameZh;

    @Size(max = 10, message = "symbol must be at most 10 characters")
    private String symbol;

    @NotNull(message = "decimalPlaces is required")
    @Min(value = 0, message = "decimalPlaces must be between 0 and 8")
    @Max(value = 8, message = "decimalPlaces must be between 0 and 8")
    private Integer decimalPlaces;

    public CurrencyCreateRequest() {
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
}
