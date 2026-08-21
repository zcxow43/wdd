package com.wdd.backend.dto;

/**
 * Body for {@code PUT /api/currency-pair-definitions/{id}}. Deliberately has
 * no {@code baseCurrencyId}/{@code quoteCurrencyId} fields — both are
 * immutable after creation. If a client sends them anyway, Spring's default
 * Jackson configuration (fail-on-unknown-properties = false) silently
 * ignores them.
 */
public class CurrencyPairDefinitionUpdateRequest {

    private Integer precision;

    public CurrencyPairDefinitionUpdateRequest() {
    }

    public CurrencyPairDefinitionUpdateRequest(Integer precision) {
        this.precision = precision;
    }

    public Integer getPrecision() {
        return precision;
    }

    public void setPrecision(Integer precision) {
        this.precision = precision;
    }
}
