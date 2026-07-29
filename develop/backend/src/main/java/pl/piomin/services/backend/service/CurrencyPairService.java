package pl.piomin.services.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pl.piomin.services.backend.dto.CurrencyPairCreateRequest;
import pl.piomin.services.backend.dto.CurrencyPairUpdateRequest;
import pl.piomin.services.backend.exception.CurrencyPairNotFoundException;
import pl.piomin.services.backend.mapper.CurrencyPairMapper;
import pl.piomin.services.backend.model.CurrencyPair;

/**
 * Applies currency-pair create/update/delete to the {@code currency_pair}
 * table. As of the audit-approval delta (specs/backend/currency-pair-approval.md),
 * these methods are no longer called directly by {@code CurrencyPairController}
 * - they are only invoked by {@code CurrencyPairAuditHandler.apply(...)} once
 * a change request has been approved. The validation rules themselves live in
 * {@link CurrencyPairValidator}, shared with {@code CurrencyPairAuditHandler}.
 */
@Service
public class CurrencyPairService {

    private final CurrencyPairMapper currencyPairMapper;
    private final CurrencyPairValidator validator;

    public CurrencyPairService(CurrencyPairMapper currencyPairMapper, CurrencyPairValidator validator) {
        this.currencyPairMapper = currencyPairMapper;
        this.validator = validator;
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
        validator.validateBrandExists(request.getBrandId());
        validator.validateCurrencyExists(request.getBaseCurrencyId());
        validator.validateCurrencyExists(request.getQuoteCurrencyId());
        validator.validateDistinct(request.getBaseCurrencyId(), request.getQuoteCurrencyId());
        validator.validateUnique(request.getBrandId(), request.getBaseCurrencyId(), request.getQuoteCurrencyId(), null);

        CurrencyPair pair = new CurrencyPair();
        pair.setBrandId(request.getBrandId());
        pair.setBaseCurrencyId(request.getBaseCurrencyId());
        pair.setQuoteCurrencyId(request.getQuoteCurrencyId());
        pair.setRateType(request.getRateType());
        pair.setActive(request.getActive() != null ? request.getActive() : Boolean.TRUE);

        // Apply rate/rateType rule immediately before persisting
        validator.applyRateTypeRule(pair, request.getRate(), null);

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
            validator.validateBrandExists(brandId);
        }
        if (request.getBaseCurrencyId() != null) {
            validator.validateCurrencyExists(baseCurrencyId);
        }
        if (request.getQuoteCurrencyId() != null) {
            validator.validateCurrencyExists(quoteCurrencyId);
        }
        validator.validateDistinct(baseCurrencyId, quoteCurrencyId);
        validator.validateUnique(brandId, baseCurrencyId, quoteCurrencyId, id);

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
        validator.applyRateTypeRule(existing, request.getRate(), existing.getRate());

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
}
