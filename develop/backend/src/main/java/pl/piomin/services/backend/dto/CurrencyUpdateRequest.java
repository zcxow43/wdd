package pl.piomin.services.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for partially updating a currency. Every field is optional,
 * but when present it must satisfy the same constraints as on create.
 * {@code code} is intentionally absent: once created, a currency's code is
 * immutable and can never be changed via this endpoint.
 */
public class CurrencyUpdateRequest {

    @Size(max = 100, message = "name must be at most 100 characters")
    private String name;

    @Size(max = 100, message = "nameZh must be at most 100 characters")
    private String nameZh;

    @Size(max = 10, message = "symbol must be at most 10 characters")
    private String symbol;

    @Min(value = 0, message = "decimalPlaces must be between 0 and 8")
    @Max(value = 8, message = "decimalPlaces must be between 0 and 8")
    private Integer decimalPlaces;

    private Boolean active;

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

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
