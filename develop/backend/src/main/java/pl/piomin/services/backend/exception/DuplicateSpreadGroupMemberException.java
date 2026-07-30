package pl.piomin.services.backend.exception;

public class DuplicateSpreadGroupMemberException extends RuntimeException {

    private final Long currencyPairId;

    public DuplicateSpreadGroupMemberException(Long currencyPairId) {
        super("Duplicate currency pair id in currencyPairIds: " + currencyPairId);
        this.currencyPairId = currencyPairId;
    }

    public Long getCurrencyPairId() {
        return currencyPairId;
    }
}
