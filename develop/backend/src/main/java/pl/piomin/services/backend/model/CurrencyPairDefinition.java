package pl.piomin.services.backend.model;

import java.time.LocalDateTime;

/**
 * Entity mapped 1:1 to the {@code currency_pair_definition} table
 * (specs/dba/currency-pair-definition.md). The {@code baseCurrencyCode} and
 * {@code quoteCurrencyCode} fields are populated only by enriched (joined)
 * read queries and are ignored on insert/update.
 */
public class CurrencyPairDefinition {

    private Long id;
    private Long baseCurrencyId;
    private String baseCurrencyCode;
    private Long quoteCurrencyId;
    private String quoteCurrencyCode;
    private Integer forwardPrecision;
    private Integer reversePrecision;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

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
