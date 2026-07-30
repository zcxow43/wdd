package pl.piomin.services.backend.dto;

import pl.piomin.services.backend.model.SpreadGroupMember;

public class SpreadGroupMemberResponse {

    private Long currencyPairId;
    private String baseCurrencyCode;
    private String quoteCurrencyCode;

    public static SpreadGroupMemberResponse from(SpreadGroupMember member) {
        SpreadGroupMemberResponse response = new SpreadGroupMemberResponse();
        response.currencyPairId = member.getCurrencyPairId();
        response.baseCurrencyCode = member.getBaseCurrencyCode();
        response.quoteCurrencyCode = member.getQuoteCurrencyCode();
        return response;
    }

    public Long getCurrencyPairId() {
        return currencyPairId;
    }

    public String getBaseCurrencyCode() {
        return baseCurrencyCode;
    }

    public String getQuoteCurrencyCode() {
        return quoteCurrencyCode;
    }
}
