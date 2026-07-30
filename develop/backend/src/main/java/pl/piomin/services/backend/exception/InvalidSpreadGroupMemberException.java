package pl.piomin.services.backend.exception;

public class InvalidSpreadGroupMemberException extends RuntimeException {

    private final Long currencyPairId;
    private final Long brandId;

    public InvalidSpreadGroupMemberException(Long currencyPairId, Long brandId) {
        super("Currency pair does not belong to the group's brand: currencyPairId=" + currencyPairId
                + ", brandId=" + brandId);
        this.currencyPairId = currencyPairId;
        this.brandId = brandId;
    }

    public Long getCurrencyPairId() {
        return currencyPairId;
    }

    public Long getBrandId() {
        return brandId;
    }
}
