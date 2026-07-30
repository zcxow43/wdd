package pl.piomin.services.backend.exception;

import java.util.List;

public class CurrencyPairDefinitionInUseException extends RuntimeException {

    private final Long baseCurrencyId;
    private final Long quoteCurrencyId;
    private final List<String> activeBrandCodes;

    public CurrencyPairDefinitionInUseException(Long baseCurrencyId, Long quoteCurrencyId,
                                                  List<String> activeBrandCodes) {
        super("One or more brands still have this currency pair active; disable it for every brand before deleting: "
                + "baseCurrencyId=" + baseCurrencyId + ", quoteCurrencyId=" + quoteCurrencyId
                + ", activeBrandCodes=" + activeBrandCodes);
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
