package com.wdd.backend.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wdd.backend.dto.CurrencyPairCreateRequest;
import com.wdd.backend.dto.CurrencyPairDefinitionCreateRequest;
import com.wdd.backend.dto.CurrencyPairDefinitionResponse;
import com.wdd.backend.dto.CurrencyPairDefinitionUpdateRequest;
import com.wdd.backend.exception.CurrencyNotFoundException;
import com.wdd.backend.exception.CurrencyPairDefinitionExistsException;
import com.wdd.backend.exception.CurrencyPairDefinitionInUseException;
import com.wdd.backend.exception.CurrencyPairDefinitionNotFoundException;
import com.wdd.backend.exception.InvalidCurrencyPairException;
import com.wdd.backend.mapper.BrandMapper;
import com.wdd.backend.mapper.CurrencyMapper;
import com.wdd.backend.mapper.CurrencyPairDefinitionMapper;
import com.wdd.backend.mapper.CurrencyPairMapper;
import com.wdd.backend.model.Brand;
import com.wdd.backend.model.CurrencyPair;
import com.wdd.backend.model.CurrencyPairDefinition;

/**
 * Additive, brand-agnostic currency pair master/definition service
 * (specs/backend/currency-pair-definition.md). Applies immediately — {@code create}/
 * {@code update}/{@code delete} do not go through the audit-approval workflow. {@code create}
 * calls the existing, unmodified {@link CurrencyPairService#create} as a plain method for its
 * per-brand fan-out; no change is made to {@link CurrencyPairService}, {@link CurrencyPairValidator},
 * or {@code CurrencyPairController}.
 */
@Service
public class CurrencyPairDefinitionService {

    private final CurrencyPairDefinitionMapper currencyPairDefinitionMapper;
    private final CurrencyPairMapper currencyPairMapper;
    private final BrandMapper brandMapper;
    private final CurrencyMapper currencyMapper;
    private final CurrencyPairService currencyPairService;

    public CurrencyPairDefinitionService(CurrencyPairDefinitionMapper currencyPairDefinitionMapper,
            CurrencyPairMapper currencyPairMapper, BrandMapper brandMapper, CurrencyMapper currencyMapper,
            CurrencyPairService currencyPairService) {
        this.currencyPairDefinitionMapper = currencyPairDefinitionMapper;
        this.currencyPairMapper = currencyPairMapper;
        this.brandMapper = brandMapper;
        this.currencyMapper = currencyMapper;
        this.currencyPairService = currencyPairService;
    }

    public List<CurrencyPairDefinitionResponse> list(Long baseCurrencyId, Long quoteCurrencyId) {
        return currencyPairDefinitionMapper.findAll(baseCurrencyId, quoteCurrencyId).stream()
                .map(CurrencyPairDefinitionResponse::from)
                .collect(Collectors.toList());
    }

    public CurrencyPairDefinitionResponse getById(Long id) {
        CurrencyPairDefinition definition = currencyPairDefinitionMapper.findById(id)
                .orElseThrow(() -> new CurrencyPairDefinitionNotFoundException(id));
        return CurrencyPairDefinitionResponse.from(definition);
    }

    /**
     * 1. Validates baseCurrencyId/quoteCurrencyId exist and differ.
     * 2. Pre-checks no existing definition matches this pair in either direction (409).
     * 3. Inserts the currency_pair_definition row.
     * 4. Fans out to every brand (brandMapper.findAll(null), unfiltered): skips any brand that
     *    already has a live (brand, base, quote) currency_pair row; otherwise calls the
     *    existing, unmodified CurrencyPairService.create(...) with rateType=AUTO/rate=null/
     *    active=true.
     * All in one transaction — if any step fails, nothing is persisted, including the
     * definition row itself.
     */
    @Transactional
    public CurrencyPairDefinitionResponse create(CurrencyPairDefinitionCreateRequest request) {
        Long baseCurrencyId = request.getBaseCurrencyId();
        Long quoteCurrencyId = request.getQuoteCurrencyId();

        if (currencyMapper.findById(baseCurrencyId).isEmpty()) {
            throw new CurrencyNotFoundException(baseCurrencyId);
        }
        if (currencyMapper.findById(quoteCurrencyId).isEmpty()) {
            throw new CurrencyNotFoundException(quoteCurrencyId);
        }
        if (baseCurrencyId.equals(quoteCurrencyId)) {
            throw new InvalidCurrencyPairException("baseCurrencyId and quoteCurrencyId must be different");
        }

        currencyPairDefinitionMapper.findByEitherDirection(baseCurrencyId, quoteCurrencyId)
                .ifPresent(existing -> {
                    throw new CurrencyPairDefinitionExistsException(baseCurrencyId, quoteCurrencyId);
                });

        CurrencyPairDefinition definition = new CurrencyPairDefinition();
        definition.setBaseCurrencyId(baseCurrencyId);
        definition.setQuoteCurrencyId(quoteCurrencyId);
        definition.setForwardPrecision(request.getForwardPrecision());
        definition.setReversePrecision(request.getReversePrecision());
        currencyPairDefinitionMapper.insert(definition);

        List<Brand> brands = brandMapper.findAll(null);
        for (Brand brand : brands) {
            boolean alreadyExists = currencyPairMapper
                    .findByBrandBaseQuote(brand.getId(), baseCurrencyId, quoteCurrencyId, null)
                    .isPresent();
            if (alreadyExists) {
                continue;
            }

            CurrencyPairCreateRequest fanOutRequest = new CurrencyPairCreateRequest();
            fanOutRequest.setBrandId(brand.getId());
            fanOutRequest.setBaseCurrencyId(baseCurrencyId);
            fanOutRequest.setQuoteCurrencyId(quoteCurrencyId);
            fanOutRequest.setRateType("AUTO");
            fanOutRequest.setRate(null);
            fanOutRequest.setActive(true);
            currencyPairService.create(fanOutRequest);
        }

        CurrencyPairDefinition created = currencyPairDefinitionMapper.findById(definition.getId())
                .orElseThrow(() -> new CurrencyPairDefinitionNotFoundException(definition.getId()));
        return CurrencyPairDefinitionResponse.from(created);
    }

    @Transactional
    public CurrencyPairDefinitionResponse update(Long id, CurrencyPairDefinitionUpdateRequest request) {
        CurrencyPairDefinition existing = currencyPairDefinitionMapper.findById(id)
                .orElseThrow(() -> new CurrencyPairDefinitionNotFoundException(id));

        existing.setForwardPrecision(request.getForwardPrecision());
        existing.setReversePrecision(request.getReversePrecision());
        currencyPairDefinitionMapper.update(existing);

        CurrencyPairDefinition updated = currencyPairDefinitionMapper.findById(id)
                .orElseThrow(() -> new CurrencyPairDefinitionNotFoundException(id));
        return CurrencyPairDefinitionResponse.from(updated);
    }

    /**
     * Deletion guard: a definition may only be deleted once every currency_pair row for its
     * (base, quote) direction, across all brands, has active = false. A brand with no row at
     * all for that pair never blocks deletion — only a live, active row does. Deletes only the
     * currency_pair_definition row — never touches currency_pair.
     */
    @Transactional
    public void delete(Long id) {
        CurrencyPairDefinition definition = currencyPairDefinitionMapper.findById(id)
                .orElseThrow(() -> new CurrencyPairDefinitionNotFoundException(id));

        List<CurrencyPair> activePairs = currencyPairMapper.findActiveByBaseQuote(definition.getBaseCurrencyId(),
                definition.getQuoteCurrencyId());
        if (!activePairs.isEmpty()) {
            List<String> activeBrandCodes = activePairs.stream()
                    .map(CurrencyPair::getBrandCode)
                    .collect(Collectors.toList());
            throw new CurrencyPairDefinitionInUseException(definition.getBaseCurrencyId(),
                    definition.getQuoteCurrencyId(), activeBrandCodes);
        }

        currencyPairDefinitionMapper.deleteById(id);
    }
}
