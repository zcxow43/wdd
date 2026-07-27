package pl.piomin.services.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import pl.piomin.services.backend.model.CurrencyPair;

/**
 * Response DTO returned by all currency pair endpoints. Includes the joined
 * {@code brandCode}, {@code baseCurrencyCode} and {@code quoteCurrencyCode}
 * so the frontend does not need extra lookups to render the table.
 */
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

    public static CurrencyPairResponse from(CurrencyPair pair) {
        CurrencyPairResponse response = new CurrencyPairResponse();
        response.id = pair.getId();
        response.brandId = pair.getBrandId();
        response.brandCode = pair.getBrandCode();
        response.baseCurrencyId = pair.getBaseCurrencyId();
        response.baseCurrencyCode = pair.getBaseCurrencyCode();
        response.quoteCurrencyId = pair.getQuoteCurrencyId();
        response.quoteCurrencyCode = pair.getQuoteCurrencyCode();
        response.rate = pair.getRate();
        response.rateType = pair.getRateType();
        response.active = pair.getActive();
        response.createdAt = pair.getCreatedAt();
        response.updatedAt = pair.getUpdatedAt();
        return response;
    }

    public Long getId() {
        return id;
    }

    public Long getBrandId() {
        return brandId;
    }

    public String getBrandCode() {
        return brandCode;
    }

    public Long getBaseCurrencyId() {
        return baseCurrencyId;
    }

    public String getBaseCurrencyCode() {
        return baseCurrencyCode;
    }

    public Long getQuoteCurrencyId() {
        return quoteCurrencyId;
    }

    public String getQuoteCurrencyCode() {
        return quoteCurrencyCode;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public String getRateType() {
        return rateType;
    }

    public Boolean getActive() {
        return active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
