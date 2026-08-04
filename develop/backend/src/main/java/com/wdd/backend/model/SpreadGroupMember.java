package com.wdd.backend.model;

import java.time.LocalDateTime;

/**
 * 1:1 with the {@code spread_group_member} table (specs/dba/spread-group-member.md) — assigns a
 * {@code currency_pair} row into a {@code spread_group}. A currency pair belongs to at most one
 * group at a time (enforced by a UNIQUE key on {@code currency_pair_id} at the DB level).
 */
public class SpreadGroupMember {

    private Long id;
    private Long spreadGroupId;
    private Long currencyPairId;
    private LocalDateTime createdAt;

    // Enrichment fields — populated only by the joined read queries, never persisted.
    private String baseCurrencyCode;
    private String quoteCurrencyCode;

    public SpreadGroupMember() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSpreadGroupId() {
        return spreadGroupId;
    }

    public void setSpreadGroupId(Long spreadGroupId) {
        this.spreadGroupId = spreadGroupId;
    }

    public Long getCurrencyPairId() {
        return currencyPairId;
    }

    public void setCurrencyPairId(Long currencyPairId) {
        this.currencyPairId = currencyPairId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
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
