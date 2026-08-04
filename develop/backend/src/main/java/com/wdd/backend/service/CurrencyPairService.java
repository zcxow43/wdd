package com.wdd.backend.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wdd.backend.dto.CurrencyPairCreateRequest;
import com.wdd.backend.dto.CurrencyPairResponse;
import com.wdd.backend.dto.CurrencyPairUpdateRequest;
import com.wdd.backend.exception.CurrencyPairNotFoundException;
import com.wdd.backend.mapper.CurrencyPairMapper;
import com.wdd.backend.model.CurrencyPair;

/**
 * {@code create} is kept exactly as-is and remains fully callable as a plain method — its sole
 * caller is {@code CurrencyPairDefinitionService}'s per-brand fan-out
 * (specs/backend/currency-pair-definition.md), which bypasses the audit workflow entirely by
 * that spec's own explicit design. It is no longer reachable from {@code CurrencyPairController}
 * (no {@code POST} route exists) nor from {@code CurrencyPairAuditHandler} (no {@code CREATE}
 * case). {@code update}/{@code delete} are only ever called from
 * {@code CurrencyPairAuditHandler.apply(...)} now, once an audit request has been approved —
 * never directly from {@code CurrencyPairController} (specs/backend/currency-pair-approval.md).
 */
@Service
public class CurrencyPairService {

    private final CurrencyPairMapper currencyPairMapper;
    private final CurrencyPairValidator validator;

    public CurrencyPairService(CurrencyPairMapper currencyPairMapper, CurrencyPairValidator validator) {
        this.currencyPairMapper = currencyPairMapper;
        this.validator = validator;
    }

    public List<CurrencyPairResponse> list(Long brandId, Boolean active) {
        return currencyPairMapper.findAll(brandId, active).stream()
                .map(CurrencyPairResponse::from)
                .collect(Collectors.toList());
    }

    public CurrencyPairResponse getById(Long id) {
        CurrencyPair pair = currencyPairMapper.findById(id)
                .orElseThrow(() -> new CurrencyPairNotFoundException(id));
        return CurrencyPairResponse.from(pair);
    }

    @Transactional
    public CurrencyPairResponse create(CurrencyPairCreateRequest request) {
        Long brandId = request.getBrandId();
        Long baseCurrencyId = request.getBaseCurrencyId();
        Long quoteCurrencyId = request.getQuoteCurrencyId();

        validator.requireBrandExists(brandId);
        validator.requireCurrencyExists(baseCurrencyId);
        validator.requireCurrencyExists(quoteCurrencyId);
        validator.requireDistinct(baseCurrencyId, quoteCurrencyId);
        validator.requireNoConflict(brandId, baseCurrencyId, quoteCurrencyId, null);

        CurrencyPair pair = new CurrencyPair();
        pair.setBrandId(brandId);
        pair.setBaseCurrencyId(baseCurrencyId);
        pair.setQuoteCurrencyId(quoteCurrencyId);
        pair.setRateType(request.getRateType());
        pair.setRate(request.getRate());
        pair.setActive(request.getActive() != null ? request.getActive() : Boolean.TRUE);

        pair.setRate(validator.applyRateTypeRule(pair.getRateType(), pair.getRate()));

        currencyPairMapper.insert(pair);

        CurrencyPair created = currencyPairMapper.findById(pair.getId())
                .orElseThrow(() -> new CurrencyPairNotFoundException(pair.getId()));
        return CurrencyPairResponse.from(created);
    }

    @Transactional
    public CurrencyPairResponse update(Long id, CurrencyPairUpdateRequest request) {
        CurrencyPair existing = currencyPairMapper.findById(id)
                .orElseThrow(() -> new CurrencyPairNotFoundException(id));

        Long brandId = request.getBrandId() != null ? request.getBrandId() : existing.getBrandId();
        Long baseCurrencyId = request.getBaseCurrencyId() != null ? request.getBaseCurrencyId()
                : existing.getBaseCurrencyId();
        Long quoteCurrencyId = request.getQuoteCurrencyId() != null ? request.getQuoteCurrencyId()
                : existing.getQuoteCurrencyId();

        if (request.getBrandId() != null) {
            validator.requireBrandExists(brandId);
        }
        if (request.getBaseCurrencyId() != null) {
            validator.requireCurrencyExists(baseCurrencyId);
        }
        if (request.getQuoteCurrencyId() != null) {
            validator.requireCurrencyExists(quoteCurrencyId);
        }
        validator.requireDistinct(baseCurrencyId, quoteCurrencyId);
        validator.requireNoConflict(brandId, baseCurrencyId, quoteCurrencyId, id);

        existing.setBrandId(brandId);
        existing.setBaseCurrencyId(baseCurrencyId);
        existing.setQuoteCurrencyId(quoteCurrencyId);
        if (request.getRateType() != null) {
            existing.setRateType(request.getRateType());
        }
        if (request.getRate() != null) {
            existing.setRate(request.getRate());
        }
        if (request.getActive() != null) {
            existing.setActive(request.getActive());
        }

        existing.setRate(validator.applyRateTypeRule(existing.getRateType(), existing.getRate()));

        currencyPairMapper.update(existing);

        CurrencyPair updated = currencyPairMapper.findById(id)
                .orElseThrow(() -> new CurrencyPairNotFoundException(id));
        return CurrencyPairResponse.from(updated);
    }

    @Transactional
    public void delete(Long id) {
        currencyPairMapper.findById(id)
                .orElseThrow(() -> new CurrencyPairNotFoundException(id));
        currencyPairMapper.deleteById(id);
    }
}
