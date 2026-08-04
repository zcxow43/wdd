package com.wdd.backend.exception;

/**
 * Thrown when a currency pair definition already exists for a (base, quote) pair in either
 * direction — the friendlier, application-level pre-check backstopped by the database's own
 * unique constraint on (pair_key_low, pair_key_high) (specs/dba/currency-pair-definition.md).
 */
public class CurrencyPairDefinitionExistsException extends RuntimeException {

    private final Long baseCurrencyId;
    private final Long quoteCurrencyId;

    public CurrencyPairDefinitionExistsException(Long baseCurrencyId, Long quoteCurrencyId) {
        super("A currency pair definition already exists for base " + baseCurrencyId + " quote " + quoteCurrencyId
                + " or its reverse direction");
        this.baseCurrencyId = baseCurrencyId;
        this.quoteCurrencyId = quoteCurrencyId;
    }

    public Long getBaseCurrencyId() {
        return baseCurrencyId;
    }

    public Long getQuoteCurrencyId() {
        return quoteCurrencyId;
    }
}
