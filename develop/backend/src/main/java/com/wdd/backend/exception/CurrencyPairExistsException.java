package com.wdd.backend.exception;

public class CurrencyPairExistsException extends RuntimeException {

    private final Long brandId;
    private final Long baseCurrencyId;
    private final Long quoteCurrencyId;

    public CurrencyPairExistsException(Long brandId, Long baseCurrencyId, Long quoteCurrencyId) {
        super("Currency pair already exists for brand " + brandId + " base " + baseCurrencyId
                + " quote " + quoteCurrencyId);
        this.brandId = brandId;
        this.baseCurrencyId = baseCurrencyId;
        this.quoteCurrencyId = quoteCurrencyId;
    }

    public Long getBrandId() {
        return brandId;
    }

    public Long getBaseCurrencyId() {
        return baseCurrencyId;
    }

    public Long getQuoteCurrencyId() {
        return quoteCurrencyId;
    }
}
