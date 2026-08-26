package com.wdd.backend.dto;

import java.math.BigDecimal;

/**
 * Response shape for {@code GET /api/spreads/effective} — one entry per
 * brand currency pair, with its resolved spread and the tier it came from
 * ({@code source}: {@code GROUP} or {@code DEFAULT}).
 */
public class EffectiveSpreadResponse {

    private Long currencyPairId;
    private Long currencyPairDefinitionId;
    private String baseCurrencyCode;
    private String quoteCurrencyCode;
    private Long brandId;
    private String brandCode;
    private Long spreadGroupId;
    private String spreadGroupName;
    private String source;
    private BigDecimal depositSpreadPercent;
    private BigDecimal withdrawalSpreadPercent;

    public EffectiveSpreadResponse() {
    }

    public EffectiveSpreadResponse(Long currencyPairId, Long currencyPairDefinitionId, String baseCurrencyCode,
            String quoteCurrencyCode, Long brandId, String brandCode, Long spreadGroupId, String spreadGroupName,
            String source, BigDecimal depositSpreadPercent, BigDecimal withdrawalSpreadPercent) {
        this.currencyPairId = currencyPairId;
        this.currencyPairDefinitionId = currencyPairDefinitionId;
        this.baseCurrencyCode = baseCurrencyCode;
        this.quoteCurrencyCode = quoteCurrencyCode;
        this.brandId = brandId;
        this.brandCode = brandCode;
        this.spreadGroupId = spreadGroupId;
        this.spreadGroupName = spreadGroupName;
        this.source = source;
        this.depositSpreadPercent = depositSpreadPercent;
        this.withdrawalSpreadPercent = withdrawalSpreadPercent;
    }

    public Long getCurrencyPairId() {
        return currencyPairId;
    }

    public void setCurrencyPairId(Long currencyPairId) {
        this.currencyPairId = currencyPairId;
    }

    public Long getCurrencyPairDefinitionId() {
        return currencyPairDefinitionId;
    }

    public void setCurrencyPairDefinitionId(Long currencyPairDefinitionId) {
        this.currencyPairDefinitionId = currencyPairDefinitionId;
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

    public Long getBrandId() {
        return brandId;
    }

    public void setBrandId(Long brandId) {
        this.brandId = brandId;
    }

    public String getBrandCode() {
        return brandCode;
    }

    public void setBrandCode(String brandCode) {
        this.brandCode = brandCode;
    }

    public Long getSpreadGroupId() {
        return spreadGroupId;
    }

    public void setSpreadGroupId(Long spreadGroupId) {
        this.spreadGroupId = spreadGroupId;
    }

    public String getSpreadGroupName() {
        return spreadGroupName;
    }

    public void setSpreadGroupName(String spreadGroupName) {
        this.spreadGroupName = spreadGroupName;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public BigDecimal getDepositSpreadPercent() {
        return depositSpreadPercent;
    }

    public void setDepositSpreadPercent(BigDecimal depositSpreadPercent) {
        this.depositSpreadPercent = depositSpreadPercent;
    }

    public BigDecimal getWithdrawalSpreadPercent() {
        return withdrawalSpreadPercent;
    }

    public void setWithdrawalSpreadPercent(BigDecimal withdrawalSpreadPercent) {
        this.withdrawalSpreadPercent = withdrawalSpreadPercent;
    }
}
