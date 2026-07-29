package pl.piomin.services.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pl.piomin.services.backend.dto.CurrencyPairCreateRequest;
import pl.piomin.services.backend.dto.CurrencyPairUpdateRequest;
import pl.piomin.services.backend.exception.BrandNotFoundException;
import pl.piomin.services.backend.exception.CurrencyNotFoundException;
import pl.piomin.services.backend.exception.CurrencyPairExistsException;
import pl.piomin.services.backend.exception.CurrencyPairNotFoundException;
import pl.piomin.services.backend.exception.InvalidCurrencyPairException;
import pl.piomin.services.backend.mapper.BrandMapper;
import pl.piomin.services.backend.mapper.CurrencyMapper;
import pl.piomin.services.backend.mapper.CurrencyPairMapper;
import pl.piomin.services.backend.model.CurrencyPair;

@Service
public class CurrencyPairService {

    private final CurrencyPairMapper currencyPairMapper;
    private final BrandMapper brandMapper;
    private final CurrencyMapper currencyMapper;

    public CurrencyPairService(CurrencyPairMapper currencyPairMapper, BrandMapper brandMapper,
                                CurrencyMapper currencyMapper) {
        this.currencyPairMapper = currencyPairMapper;
        this.brandMapper = brandMapper;
        this.currencyMapper = currencyMapper;
    }

    public List<CurrencyPair> list(Long brandId, Boolean active) {
        return currencyPairMapper.findAll(brandId, active);
    }

    public CurrencyPair getById(Long id) {
        CurrencyPair pair = currencyPairMapper.findById(id);
        if (pair == null) {
            throw new CurrencyPairNotFoundException(id);
        }
        return pair;
    }

    @Transactional
    public CurrencyPair create(CurrencyPairCreateRequest request) {
        validateBrandExists(request.getBrandId());
        validateCurrencyExists(request.getBaseCurrencyId());
        validateCurrencyExists(request.getQuoteCurrencyId());
        validateDistinct(request.getBaseCurrencyId(), request.getQuoteCurrencyId());
        validateUnique(request.getBrandId(), request.getBaseCurrencyId(), request.getQuoteCurrencyId(), null);

        CurrencyPair pair = new CurrencyPair();
        pair.setBrandId(request.getBrandId());
        pair.setBaseCurrencyId(request.getBaseCurrencyId());
        pair.setQuoteCurrencyId(request.getQuoteCurrencyId());
        pair.setRateType(request.getRateType());
        pair.setActive(request.getActive() != null ? request.getActive() : Boolean.TRUE);

        // Apply rate/rateType rule immediately before persisting
        applyRateTypeRule(pair, request.getRate(), null);

        currencyPairMapper.insert(pair);
        return currencyPairMapper.findById(pair.getId());
    }

    @Transactional
    public CurrencyPair update(Long id, CurrencyPairUpdateRequest request) {
        CurrencyPair existing = currencyPairMapper.findById(id);
        if (existing == null) {
            throw new CurrencyPairNotFoundException(id);
        }

        Long brandId = request.getBrandId() != null ? request.getBrandId() : existing.getBrandId();
        Long baseCurrencyId = request.getBaseCurrencyId() != null
                ? request.getBaseCurrencyId() : existing.getBaseCurrencyId();
        Long quoteCurrencyId = request.getQuoteCurrencyId() != null
                ? request.getQuoteCurrencyId() : existing.getQuoteCurrencyId();

        if (request.getBrandId() != null) {
            validateBrandExists(brandId);
        }
        if (request.getBaseCurrencyId() != null) {
            validateCurrencyExists(baseCurrencyId);
        }
        if (request.getQuoteCurrencyId() != null) {
            validateCurrencyExists(quoteCurrencyId);
        }
        validateDistinct(baseCurrencyId, quoteCurrencyId);
        validateUnique(brandId, baseCurrencyId, quoteCurrencyId, id);

        existing.setBrandId(brandId);
        existing.setBaseCurrencyId(baseCurrencyId);
        existing.setQuoteCurrencyId(quoteCurrencyId);
        if (request.getActive() != null) {
            existing.setActive(request.getActive());
        }

        // Resolve effective rateType and rate
        String effectiveRateType = request.getRateType() != null ? request.getRateType() : existing.getRateType();
        existing.setRateType(effectiveRateType);

        // Apply rate/rateType rule immediately before persisting
        applyRateTypeRule(existing, request.getRate(), existing.getRate());

        currencyPairMapper.update(existing);
        return currencyPairMapper.findById(id);
    }

    @Transactional
    public void delete(Long id) {
        CurrencyPair existing = currencyPairMapper.findById(id);
        if (existing == null) {
            throw new CurrencyPairNotFoundException(id);
        }
        currencyPairMapper.deleteById(id);
    }

    private void validateBrandExists(Long brandId) {
        if (brandMapper.findById(brandId) == null) {
            throw new BrandNotFoundException(brandId);
        }
    }

    private void validateCurrencyExists(Long currencyId) {
        if (currencyMapper.findById(currencyId) == null) {
            throw new CurrencyNotFoundException(currencyId);
        }
    }

    private void validateDistinct(Long baseCurrencyId, Long quoteCurrencyId) {
        if (baseCurrencyId != null && baseCurrencyId.equals(quoteCurrencyId)) {
            throw new InvalidCurrencyPairException("Base and quote currency must differ");
        }
    }

    private void validateUnique(Long brandId, Long baseCurrencyId, Long quoteCurrencyId, Long excludeId) {
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
     * @param pair the entity to modify
     * @param requestRate the rate supplied in the request (may be null)
     * @param fallbackRate the existing rate from the DB row (used on update when requestRate is null; may be null)
     */
    private void applyRateTypeRule(CurrencyPair pair, java.math.BigDecimal requestRate,
                                     java.math.BigDecimal fallbackRate) {
        String effectiveRateType = pair.getRateType();

        if ("AUTO".equals(effectiveRateType)) {
            // Force rate to null, discarding any supplied value
            pair.setRate(null);
        } else if ("MANUAL".equals(effectiveRateType)) {
            // Resolve effective rate: prefer requestRate, fall back to existing
            java.math.BigDecimal effectiveRate = requestRate != null ? requestRate : fallbackRate;
            if (effectiveRate == null || effectiveRate.compareTo(java.math.BigDecimal.ZERO) <= 0) {
                throw new InvalidCurrencyPairException("rate is required and must be greater than 0 when rateType is MANUAL");
            }
            pair.setRate(effectiveRate);
        }
    }
}
