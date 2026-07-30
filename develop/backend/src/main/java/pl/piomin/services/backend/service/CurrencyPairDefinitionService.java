package pl.piomin.services.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pl.piomin.services.backend.dto.CurrencyPairCreateRequest;
import pl.piomin.services.backend.dto.CurrencyPairDefinitionCreateRequest;
import pl.piomin.services.backend.dto.CurrencyPairDefinitionUpdateRequest;
import pl.piomin.services.backend.exception.CurrencyNotFoundException;
import pl.piomin.services.backend.exception.CurrencyPairDefinitionExistsException;
import pl.piomin.services.backend.exception.CurrencyPairDefinitionInUseException;
import pl.piomin.services.backend.exception.CurrencyPairDefinitionNotFoundException;
import pl.piomin.services.backend.exception.InvalidCurrencyPairException;
import pl.piomin.services.backend.mapper.BrandMapper;
import pl.piomin.services.backend.mapper.CurrencyMapper;
import pl.piomin.services.backend.mapper.CurrencyPairDefinitionMapper;
import pl.piomin.services.backend.mapper.CurrencyPairMapper;
import pl.piomin.services.backend.model.Brand;
import pl.piomin.services.backend.model.CurrencyPair;
import pl.piomin.services.backend.model.CurrencyPairDefinition;

/**
 * Creates/edits/deletes brand-agnostic currency pair definitions
 * (specs/backend/currency-pair-definition.md) and, on create, fans out a
 * {@code currency_pair} row to every brand that doesn't already have one for
 * the exact (brand, base, quote) triple. Unlike the currency-pair/spread
 * features, this applies immediately - it never goes through the generic
 * audit-approval workflow. The fan-out reuses the existing, unmodified
 * {@link CurrencyPairService#create} as a plain method call.
 */
@Service
public class CurrencyPairDefinitionService {

    private final CurrencyPairDefinitionMapper currencyPairDefinitionMapper;
    private final CurrencyPairMapper currencyPairMapper;
    private final CurrencyMapper currencyMapper;
    private final BrandMapper brandMapper;
    private final CurrencyPairService currencyPairService;

    public CurrencyPairDefinitionService(CurrencyPairDefinitionMapper currencyPairDefinitionMapper,
                                          CurrencyPairMapper currencyPairMapper,
                                          CurrencyMapper currencyMapper,
                                          BrandMapper brandMapper,
                                          CurrencyPairService currencyPairService) {
        this.currencyPairDefinitionMapper = currencyPairDefinitionMapper;
        this.currencyPairMapper = currencyPairMapper;
        this.currencyMapper = currencyMapper;
        this.brandMapper = brandMapper;
        this.currencyPairService = currencyPairService;
    }

    public List<CurrencyPairDefinition> list(Long baseCurrencyId, Long quoteCurrencyId) {
        return currencyPairDefinitionMapper.findAll(baseCurrencyId, quoteCurrencyId);
    }

    public CurrencyPairDefinition getById(Long id) {
        CurrencyPairDefinition definition = currencyPairDefinitionMapper.findById(id);
        if (definition == null) {
            throw new CurrencyPairDefinitionNotFoundException(id);
        }
        return definition;
    }

    @Transactional
    public CurrencyPairDefinition create(CurrencyPairDefinitionCreateRequest request) {
        Long baseCurrencyId = request.getBaseCurrencyId();
        Long quoteCurrencyId = request.getQuoteCurrencyId();

        if (currencyMapper.findById(baseCurrencyId) == null) {
            throw new CurrencyNotFoundException(baseCurrencyId);
        }
        if (currencyMapper.findById(quoteCurrencyId) == null) {
            throw new CurrencyNotFoundException(quoteCurrencyId);
        }
        if (baseCurrencyId.equals(quoteCurrencyId)) {
            throw new InvalidCurrencyPairException("Base and quote currency must differ");
        }
        if (currencyPairDefinitionMapper.findByEitherDirection(baseCurrencyId, quoteCurrencyId) != null) {
            throw new CurrencyPairDefinitionExistsException(baseCurrencyId, quoteCurrencyId);
        }

        CurrencyPairDefinition definition = new CurrencyPairDefinition();
        definition.setBaseCurrencyId(baseCurrencyId);
        definition.setQuoteCurrencyId(quoteCurrencyId);
        definition.setForwardPrecision(request.getForwardPrecision());
        definition.setReversePrecision(request.getReversePrecision());
        currencyPairDefinitionMapper.insert(definition);

        for (Brand brand : brandMapper.findAll(null)) {
            if (currencyPairMapper.findByBrandBaseQuote(brand.getId(), baseCurrencyId, quoteCurrencyId) != null) {
                continue;
            }
            CurrencyPairCreateRequest fanOutRequest = new CurrencyPairCreateRequest();
            fanOutRequest.setBrandId(brand.getId());
            fanOutRequest.setBaseCurrencyId(baseCurrencyId);
            fanOutRequest.setQuoteCurrencyId(quoteCurrencyId);
            fanOutRequest.setRateType("AUTO");
            fanOutRequest.setRate(null);
            fanOutRequest.setActive(Boolean.TRUE);
            currencyPairService.create(fanOutRequest);
        }

        return currencyPairDefinitionMapper.findById(definition.getId());
    }

    @Transactional
    public CurrencyPairDefinition update(Long id, CurrencyPairDefinitionUpdateRequest request) {
        CurrencyPairDefinition existing = currencyPairDefinitionMapper.findById(id);
        if (existing == null) {
            throw new CurrencyPairDefinitionNotFoundException(id);
        }
        existing.setForwardPrecision(request.getForwardPrecision());
        existing.setReversePrecision(request.getReversePrecision());
        currencyPairDefinitionMapper.update(existing);
        return currencyPairDefinitionMapper.findById(id);
    }

    @Transactional
    public void delete(Long id) {
        CurrencyPairDefinition definition = currencyPairDefinitionMapper.findById(id);
        if (definition == null) {
            throw new CurrencyPairDefinitionNotFoundException(id);
        }
        List<CurrencyPair> activePairs = currencyPairMapper.findActiveByBaseQuote(
                definition.getBaseCurrencyId(), definition.getQuoteCurrencyId());
        if (!activePairs.isEmpty()) {
            List<String> activeBrandCodes = activePairs.stream()
                    .map(CurrencyPair::getBrandCode)
                    .toList();
            throw new CurrencyPairDefinitionInUseException(
                    definition.getBaseCurrencyId(), definition.getQuoteCurrencyId(), activeBrandCodes);
        }
        currencyPairDefinitionMapper.deleteById(id);
    }
}
