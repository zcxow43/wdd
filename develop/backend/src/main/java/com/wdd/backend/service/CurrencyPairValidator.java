package com.wdd.backend.service;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.wdd.backend.exception.BrandNotFoundException;
import com.wdd.backend.exception.CurrencyNotFoundException;
import com.wdd.backend.exception.CurrencyPairExistsException;
import com.wdd.backend.exception.InvalidCurrencyPairException;
import com.wdd.backend.mapper.BrandMapper;
import com.wdd.backend.mapper.CurrencyMapper;
import com.wdd.backend.mapper.CurrencyPairMapper;
import com.wdd.backend.model.Brand;
import com.wdd.backend.model.Currency;

/**
 * Shared brand/currency-existence, base != quote, uniqueness, and rate/rateType business-rule
 * validation for currency pairs — extracted out of {@link CurrencyPairService} so it can be
 * reused, verbatim, by {@link CurrencyPairAuditHandler} (which validates a proposed UPDATE
 * snapshot before it is ever applied to the {@code currency_pair} table).
 */
@Component
public class CurrencyPairValidator {

    private static final String RATE_TYPE_MANUAL = "MANUAL";
    private static final String RATE_TYPE_AUTO = "AUTO";

    private final BrandMapper brandMapper;
    private final CurrencyMapper currencyMapper;
    private final CurrencyPairMapper currencyPairMapper;

    public CurrencyPairValidator(BrandMapper brandMapper, CurrencyMapper currencyMapper,
            CurrencyPairMapper currencyPairMapper) {
        this.brandMapper = brandMapper;
        this.currencyMapper = currencyMapper;
        this.currencyPairMapper = currencyPairMapper;
    }

    public Brand requireBrandExists(Long brandId) {
        return brandMapper.findById(brandId).orElseThrow(() -> new BrandNotFoundException(brandId));
    }

    public Currency requireCurrencyExists(Long currencyId) {
        return currencyMapper.findById(currencyId).orElseThrow(() -> new CurrencyNotFoundException(currencyId));
    }

    public void requireDistinct(Long baseCurrencyId, Long quoteCurrencyId) {
        if (baseCurrencyId != null && baseCurrencyId.equals(quoteCurrencyId)) {
            throw new InvalidCurrencyPairException("baseCurrencyId and quoteCurrencyId must be different");
        }
    }

    /**
     * Uniqueness check for (brandId, baseCurrencyId, quoteCurrencyId), optionally excluding a
     * given row id — pass {@code excludeId = null} on create, or the row's own id on update.
     */
    public void requireNoConflict(Long brandId, Long baseCurrencyId, Long quoteCurrencyId, Long excludeId) {
        currencyPairMapper.findByBrandBaseQuote(brandId, baseCurrencyId, quoteCurrencyId, excludeId)
                .ifPresent(other -> {
                    throw new CurrencyPairExistsException(brandId, baseCurrencyId, quoteCurrencyId);
                });
    }

    /**
     * Applies the rate/rateType business rule, returning the persistable rate:
     * - rateType == AUTO -> returns null, discarding whatever rate was supplied (never rejected).
     * - rateType == MANUAL -> the given (already-effective/merged) rate must be non-null and > 0,
     *   else throws {@link InvalidCurrencyPairException} (400).
     */
    public BigDecimal applyRateTypeRule(String rateType, BigDecimal rate) {
        if (RATE_TYPE_AUTO.equals(rateType)) {
            return null;
        }
        if (RATE_TYPE_MANUAL.equals(rateType)) {
            if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
                throw new InvalidCurrencyPairException(
                        "rate is required and must be greater than 0 when rateType is MANUAL");
            }
        }
        return rate;
    }
}
