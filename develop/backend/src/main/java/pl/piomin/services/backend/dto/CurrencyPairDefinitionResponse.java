package pl.piomin.services.backend.dto;

import java.time.LocalDateTime;

import pl.piomin.services.backend.model.CurrencyPairDefinition;

/**
 * Response DTO returned by all currency pair definition endpoints. Includes
 * the joined {@code baseCurrencyCode}/{@code quoteCurrencyCode} so the
 * frontend does not need extra lookups to render the table.
 */
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

    public static CurrencyPairDefinitionResponse from(CurrencyPairDefinition definition) {
        CurrencyPairDefinitionResponse response = new CurrencyPairDefinitionResponse();
        response.id = definition.getId();
        response.baseCurrencyId = definition.getBaseCurrencyId();
        response.baseCurrencyCode = definition.getBaseCurrencyCode();
        response.quoteCurrencyId = definition.getQuoteCurrencyId();
        response.quoteCurrencyCode = definition.getQuoteCurrencyCode();
        response.forwardPrecision = definition.getForwardPrecision();
        response.reversePrecision = definition.getReversePrecision();
        response.createdAt = definition.getCreatedAt();
        response.updatedAt = definition.getUpdatedAt();
        return response;
    }

    public Long getId() {
        return id;
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

    public Integer getForwardPrecision() {
        return forwardPrecision;
    }

    public Integer getReversePrecision() {
        return reversePrecision;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
