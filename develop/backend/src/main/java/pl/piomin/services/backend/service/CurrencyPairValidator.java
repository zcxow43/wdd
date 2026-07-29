package pl.piomin.services.backend.service;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import pl.piomin.services.backend.exception.BrandNotFoundException;
import pl.piomin.services.backend.exception.CurrencyNotFoundException;
import pl.piomin.services.backend.exception.CurrencyPairExistsException;
import pl.piomin.services.backend.exception.InvalidCurrencyPairException;
import pl.piomin.services.backend.mapper.BrandMapper;
import pl.piomin.services.backend.mapper.CurrencyMapper;
import pl.piomin.services.backend.mapper.CurrencyPairMapper;
import pl.piomin.services.backend.model.Brand;
import pl.piomin.services.backend.model.Currency;
import pl.piomin.services.backend.model.CurrencyPair;

/**
 * Shared currency-pair business-rule validation: brand/currency existence,
 * base != quote, (brand, base, quote) uniqueness, and the rate/rateType rule.
 * Extracted out of {@link CurrencyPairService} so the exact same rules can be
 * reused by {@link CurrencyPairAuditHandler} when validating a proposed
 * change at submit/approve time, without duplicating the logic.
 */
@Component
public class CurrencyPairValidator {

    private final BrandMapper brandMapper;
    private final CurrencyMapper currencyMapper;
    private final CurrencyPairMapper currencyPairMapper;

    public CurrencyPairValidator(BrandMapper brandMapper, CurrencyMapper currencyMapper,
                                  CurrencyPairMapper currencyPairMapper) {
        this.brandMapper = brandMapper;
        this.currencyMapper = currencyMapper;
        this.currencyPairMapper = currencyPairMapper;
    }

    public Brand getBrand(Long brandId) {
        return brandMapper.findById(brandId);
    }

    public Currency getCurrency(Long currencyId) {
        return currencyMapper.findById(currencyId);
    }

    public void validateBrandExists(Long brandId) {
        if (brandMapper.findById(brandId) == null) {
            throw new BrandNotFoundException(brandId);
        }
    }

    public void validateCurrencyExists(Long currencyId) {
        if (currencyMapper.findById(currencyId) == null) {
            throw new CurrencyNotFoundException(currencyId);
        }
    }

    public void validateDistinct(Long baseCurrencyId, Long quoteCurrencyId) {
        if (baseCurrencyId != null && baseCurrencyId.equals(quoteCurrencyId)) {
            throw new InvalidCurrencyPairException("Base and quote currency must differ");
        }
    }

    public void validateUnique(Long brandId, Long baseCurrencyId, Long quoteCurrencyId, Long excludeId) {
        CurrencyPair other = currencyPairMapper.findByBrandBaseQuote(brandId, baseCurrencyId, quoteCurrencyId);
        if (other != null && !other.getId().equals(excludeId)) {
            throw new CurrencyPairExistsException(brandId, baseCurrencyId, quoteCurrencyId);
        }
    }

    /**
     * Apply rate/rateType business rule immediately before persisting.
     * - AUTO: force rate = null, ignoring any supplied value
     * - MANUAL: rate must be non-null and > 0 (resolve from requestRate or fallbackRate)
     *
     * @param pair the entity to modify (must already have {@code rateType} set)
     * @param requestRate the rate supplied in the request (may be null)
     * @param fallbackRate the existing rate from the DB row (used on update when requestRate is null; may be null)
     */
    public void applyRateTypeRule(CurrencyPair pair, BigDecimal requestRate, BigDecimal fallbackRate) {
        String effectiveRateType = pair.getRateType();

        if ("AUTO".equals(effectiveRateType)) {
            pair.setRate(null);
        } else if ("MANUAL".equals(effectiveRateType)) {
            BigDecimal effectiveRate = requestRate != null ? requestRate : fallbackRate;
            if (effectiveRate == null || effectiveRate.compareTo(BigDecimal.ZERO) <= 0) {
                throw new InvalidCurrencyPairException(
                        "rate is required and must be greater than 0 when rateType is MANUAL");
            }
            pair.setRate(effectiveRate);
        }
    }
}
