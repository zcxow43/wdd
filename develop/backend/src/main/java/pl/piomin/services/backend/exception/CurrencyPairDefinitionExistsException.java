package pl.piomin.services.backend.exception;

public class CurrencyPairDefinitionExistsException extends RuntimeException {

    private final Long baseCurrencyId;
    private final Long quoteCurrencyId;

    public CurrencyPairDefinitionExistsException(Long baseCurrencyId, Long quoteCurrencyId) {
        super("A currency pair definition already exists for this pair or its reverse direction: "
                + "baseCurrencyId=" + baseCurrencyId + ", quoteCurrencyId=" + quoteCurrencyId);
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
