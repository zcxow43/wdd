package com.wdd.backend.dto;

import java.util.List;

/**
 * Body for {@code POST /api/spread-groups/{id}/members}. Must be a
 * non-empty list of {@code currency_pair.id}s.
 */
public class SpreadGroupMemberAssignRequest {

    private List<Long> currencyPairIds;

    public SpreadGroupMemberAssignRequest() {
    }

    public SpreadGroupMemberAssignRequest(List<Long> currencyPairIds) {
        this.currencyPairIds = currencyPairIds;
    }

    public List<Long> getCurrencyPairIds() {
        return currencyPairIds;
    }

    public void setCurrencyPairIds(List<Long> currencyPairIds) {
        this.currencyPairIds = currencyPairIds;
    }
}
