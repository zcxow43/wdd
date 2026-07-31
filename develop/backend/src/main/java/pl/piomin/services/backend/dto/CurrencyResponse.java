package pl.piomin.services.backend.dto;

import java.time.LocalDateTime;

import pl.piomin.services.backend.model.Currency;

/**
 * Response DTO returned by all currency endpoints.
 */
public class CurrencyResponse {

    private Long id;
    private String code;
    private String name;
    private String nameZh;
    private String symbol;
    private Integer decimalPlaces;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CurrencyResponse from(Currency currency) {
        CurrencyResponse response = new CurrencyResponse();
        response.id = currency.getId();
        response.code = currency.getCode();
        response.name = currency.getName();
        response.nameZh = currency.getNameZh();
        response.symbol = currency.getSymbol();
        response.decimalPlaces = currency.getDecimalPlaces();
        response.createdAt = currency.getCreatedAt();
        response.updatedAt = currency.getUpdatedAt();
        return response;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getNameZh() {
        return nameZh;
    }

    public String getSymbol() {
        return symbol;
    }

    public Integer getDecimalPlaces() {
        return decimalPlaces;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
