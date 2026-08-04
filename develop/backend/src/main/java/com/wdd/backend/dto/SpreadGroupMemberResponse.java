package com.wdd.backend.dto;

import com.wdd.backend.model.SpreadGroupMember;

public class SpreadGroupMemberResponse {

    private Long currencyPairId;
    private String baseCurrencyCode;
    private String quoteCurrencyCode;

    public SpreadGroupMemberResponse() {
    }

    public static SpreadGroupMemberResponse from(SpreadGroupMember member) {
        SpreadGroupMemberResponse response = new SpreadGroupMemberResponse();
        response.setCurrencyPairId(member.getCurrencyPairId());
        response.setBaseCurrencyCode(member.getBaseCurrencyCode());
        response.setQuoteCurrencyCode(member.getQuoteCurrencyCode());
        return response;
    }

    public Long getCurrencyPairId() {
        return currencyPairId;
    }

    public void setCurrencyPairId(Long currencyPairId) {
        this.currencyPairId = currencyPairId;
    }

    public String getBaseCurrencyCode() {
        return baseCurrencyCode;
    }

    public void setBaseCurrencyCode(String baseCurrencyCode) {
        this.baseCurrencyCode = baseCurrencyCode;
    }

    public String getQuoteCurrencyCode() {
        return quoteCurrencyCode;
    }

    public void setQuoteCurrencyCode(String quoteCurrencyCode) {
        this.quoteCurrencyCode = quoteCurrencyCode;
    }
}
