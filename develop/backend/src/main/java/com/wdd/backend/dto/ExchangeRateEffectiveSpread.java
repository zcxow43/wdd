package com.wdd.backend.dto;

import java.math.BigDecimal;

/**
 * One {@code (currency_pair_definition, brand)} combination's
 * currently-effective deposit/withdrawal spread, resolved entirely in SQL —
 * the group's spread if that brand's {@code currency_pair} row for this
 * definition is assigned to a {@code spread_group}, otherwise the brand's
 * default from {@code brand_spread}. This is the same resolution
 * {@link com.wdd.backend.mapper.CurrencyPairMapper#findEffectiveSpreadsByBrandId}
 * implements for {@code GET /api/currency-pairs}'s live-computed
 * {@code depositRate}/{@code withdrawalRate}, replicated here as its own
 * query (per this project's small-deliberate-duplication convention) because
 * this feature needs it fanned out across every definition and every brand
 * at once, not scoped to a single brand.
 */
public class ExchangeRateEffectiveSpread {

    private Long currencyPairDefinitionId;
    private Long brandId;
    private String brandCode;
    private BigDecimal depositSpreadPercent;
    private BigDecimal withdrawalSpreadPercent;

    public Long getCurrencyPairDefinitionId() {
        return currencyPairDefinitionId;
    }

    public void setCurrencyPairDefinitionId(Long currencyPairDefinitionId) {
        this.currencyPairDefinitionId = currencyPairDefinitionId;
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
