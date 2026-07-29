package pl.piomin.services.backend.exception;

/**
 * Thrown by {@code CurrencyPairAuditHandler} when a CREATE is submitted for a
 * (brandId, baseCurrencyId, quoteCurrencyId) combination that already has a
 * PENDING CREATE audit request. This natural-key dedup rule is the handler's
 * own responsibility (specs/backend/audit.md) since there is no entityId yet
 * for the generic audit module to dedup on.
 */
public class DuplicatePendingCurrencyPairCreateException extends RuntimeException {

    private final Long brandId;
    private final Long baseCurrencyId;
    private final Long quoteCurrencyId;

    public DuplicatePendingCurrencyPairCreateException(Long brandId, Long baseCurrencyId, Long quoteCurrencyId) {
        super("A pending create request already exists for this brand/base/quote combination");
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
