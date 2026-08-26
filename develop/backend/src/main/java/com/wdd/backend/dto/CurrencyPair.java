package com.wdd.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Persistence model mapped to the {@code currency_pair} table (a brand's
 * pair under a {@link CurrencyPairDefinition}). {@code brandCode},
 * {@code baseCurrencyCode}/{@code quoteCurrencyCode}, and
 * {@code spreadGroupId}/{@code spreadGroupName} are read-only enrichment
 * fields populated by joins against {@code brand}, (via the parent
 * definition) {@code currency}, and {@code spread_group} respectively.
 *
 * <p>{@code autoRate}, {@code effectiveDepositSpreadPercent}, and
 * {@code effectiveWithdrawalSpreadPercent} are further read-only enrichment
 * fields, populated by the same read query via a {@code LEFT JOIN} to the
 * latest synced {@code exchange_rate} row for this pair's definition and a
 * resolved (group-or-default) spread percentage lookup against
 * {@code spread_group}/{@code brand_spread}. They exist purely to let
 * {@link #getDepositRate()}/{@link #getWithdrawalRate()} compute this pair's
 * 入金/出金加點完成 rate on the fly, applying the percentage as a
 * multiplicative markup (@code baseRate * (1 + percent / 100)}) — none of
 * the three is itself part of the API response.
 */
public class CurrencyPair {

    private static final String RATE_TYPE_MANUAL = "MANUAL";
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private Long id;
    private Long currencyPairDefinitionId;
    private String baseCurrencyCode;
    private String quoteCurrencyCode;
    private Long brandId;
    private String brandCode;
    private String rateType;
    private BigDecimal rate;
    private Boolean active;
    private Long spreadGroupId;
    private String spreadGroupName;
    private BigDecimal autoRate;
    private BigDecimal effectiveDepositSpreadPercent;
    private BigDecimal effectiveWithdrawalSpreadPercent;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getRateType() {
        return rateType;
    }

    public void setRateType(String rateType) {
        this.rateType = rateType;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public void setRate(BigDecimal rate) {
        this.rate = rate;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
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

    public BigDecimal getAutoRate() {
        return autoRate;
    }

    public void setAutoRate(BigDecimal autoRate) {
        this.autoRate = autoRate;
    }

    public BigDecimal getEffectiveDepositSpreadPercent() {
        return effectiveDepositSpreadPercent;
    }

    public void setEffectiveDepositSpreadPercent(BigDecimal effectiveDepositSpreadPercent) {
        this.effectiveDepositSpreadPercent = effectiveDepositSpreadPercent;
    }

    public BigDecimal getEffectiveWithdrawalSpreadPercent() {
        return effectiveWithdrawalSpreadPercent;
    }

    public void setEffectiveWithdrawalSpreadPercent(BigDecimal effectiveWithdrawalSpreadPercent) {
        this.effectiveWithdrawalSpreadPercent = effectiveWithdrawalSpreadPercent;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * This pair's base rate: its own {@code rate} when {@code MANUAL}, or the
     * latest synced {@code exchange_rate.rate} for its definition
     * ({@code autoRate}, possibly {@code null} if never synced) when
     * {@code AUTO}.
     */
    private BigDecimal getBaseRate() {
        return RATE_TYPE_MANUAL.equals(rateType) ? rate : autoRate;
    }

    /**
     * 入金加點完成 — this pair's base rate multiplied by
     * {@code (1 + effectiveDepositSpreadPercent / 100)}, a percentage markup
     * (not a flat amount added). {@code null} when the base rate is
     * unavailable (an {@code AUTO} pair whose definition has never been
     * synced).
     */
    public BigDecimal getDepositRate() {
        return applySpreadPercent(effectiveDepositSpreadPercent);
    }

    /**
     * 出金加點完成 — same computation as {@link #getDepositRate()} using the
     * effective {@code withdrawalSpreadPercent} instead.
     */
    public BigDecimal getWithdrawalRate() {
        return applySpreadPercent(effectiveWithdrawalSpreadPercent);
    }

    private BigDecimal applySpreadPercent(BigDecimal spreadPercent) {
        BigDecimal baseRate = getBaseRate();
        if (baseRate == null || spreadPercent == null) {
            return null;
        }
        BigDecimal multiplier = BigDecimal.ONE.add(spreadPercent.divide(HUNDRED));
        return baseRate.multiply(multiplier);
    }
}
