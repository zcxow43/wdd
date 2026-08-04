package com.wdd.backend.exception;

import java.util.List;

/**
 * Thrown by {@code CurrencyPairDefinitionService.delete} when one or more brands still have an
 * {@code active = true} {@code currency_pair} row for the definition's (base, quote) direction —
 * the caller must disable it for every brand first (specs/backend/currency-pair-approval.md)
 * before the definition itself may be deleted.
 */
public class CurrencyPairDefinitionInUseException extends RuntimeException {

    private final Long baseCurrencyId;
    private final Long quoteCurrencyId;
    private final List<String> activeBrandCodes;

    public CurrencyPairDefinitionInUseException(Long baseCurrencyId, Long quoteCurrencyId,
            List<String> activeBrandCodes) {
        super("One or more brands still have this currency pair active; disable it for every brand before deleting: "
                + activeBrandCodes);
        this.baseCurrencyId = baseCurrencyId;
        this.quoteCurrencyId = quoteCurrencyId;
        this.activeBrandCodes = activeBrandCodes;
    }

    public Long getBaseCurrencyId() {
        return baseCurrencyId;
    }

    public Long getQuoteCurrencyId() {
        return quoteCurrencyId;
    }

    public List<String> getActiveBrandCodes() {
        return activeBrandCodes;
    }
}
