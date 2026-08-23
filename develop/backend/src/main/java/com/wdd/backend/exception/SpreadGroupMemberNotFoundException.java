package com.wdd.backend.exception;

/**
 * Thrown by {@code DELETE /api/spread-groups/{id}/members/{currencyPairId}}
 * when the currency pair is not currently a member of that group (whether
 * because it belongs to no group, a different group, or does not exist).
 */
public class SpreadGroupMemberNotFoundException extends RuntimeException {

    public SpreadGroupMemberNotFoundException(Long spreadGroupId, Long currencyPairId) {
        super("Currency pair " + currencyPairId + " is not a member of spread group " + spreadGroupId);
    }
}
