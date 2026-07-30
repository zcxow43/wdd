package pl.piomin.services.backend.model;

import java.time.LocalDateTime;

/**
 * Entity mapped 1:1 to the {@code spread_group_member} table. {@code baseCurrencyCode}
 * and {@code quoteCurrencyCode} are populated only by enriched (joined) read
 * queries (via {@code currency_pair} -> {@code currency}) and are ignored on insert.
 */
public class SpreadGroupMember {

    private Long id;
    private Long spreadGroupId;
    private Long currencyPairId;
    private String baseCurrencyCode;
    private String quoteCurrencyCode;
    private LocalDateTime createdAt;

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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
