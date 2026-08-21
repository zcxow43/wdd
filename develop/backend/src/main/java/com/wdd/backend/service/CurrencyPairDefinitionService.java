package com.wdd.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wdd.backend.dto.Brand;
import com.wdd.backend.dto.CurrencyPair;
import com.wdd.backend.dto.CurrencyPairDefinition;
import com.wdd.backend.dto.CurrencyPairDefinitionCreateRequest;
import com.wdd.backend.dto.CurrencyPairDefinitionCreateResponse;
import com.wdd.backend.dto.CurrencyPairDefinitionResponse;
import com.wdd.backend.dto.CurrencyPairDefinitionUpdateRequest;
import com.wdd.backend.dto.CurrencyPairResponse;
import com.wdd.backend.exception.ActiveCurrencyPairsExistException;
import com.wdd.backend.exception.CurrencyPairDefinitionConflictException;
import com.wdd.backend.exception.CurrencyPairDefinitionNotFoundException;
import com.wdd.backend.exception.InvalidRequestException;
import com.wdd.backend.mapper.BrandMapper;
import com.wdd.backend.mapper.CurrencyMapper;
import com.wdd.backend.mapper.CurrencyPairDefinitionMapper;
import com.wdd.backend.mapper.CurrencyPairMapper;

@Service
public class CurrencyPairDefinitionService {

    private static final int DEFAULT_PRECISION = 4;
    private static final int MIN_PRECISION = 0;
    private static final int MAX_PRECISION = 8;

    private final CurrencyPairDefinitionMapper currencyPairDefinitionMapper;
    private final CurrencyPairMapper currencyPairMapper;
    private final CurrencyMapper currencyMapper;
    private final BrandMapper brandMapper;

    public CurrencyPairDefinitionService(CurrencyPairDefinitionMapper currencyPairDefinitionMapper,
            CurrencyPairMapper currencyPairMapper, CurrencyMapper currencyMapper, BrandMapper brandMapper) {
        this.currencyPairDefinitionMapper = currencyPairDefinitionMapper;
        this.currencyPairMapper = currencyPairMapper;
        this.currencyMapper = currencyMapper;
        this.brandMapper = brandMapper;
    }

    public List<CurrencyPairDefinitionResponse> findAll() {
        return currencyPairDefinitionMapper.findAll().stream()
                .map(CurrencyPairDefinitionService::toResponse)
                .toList();
    }

    public CurrencyPairDefinitionResponse findById(Long id) {
        CurrencyPairDefinition definition = currencyPairDefinitionMapper.findById(id);
        if (definition == null) {
            throw new CurrencyPairDefinitionNotFoundException(id);
        }
        return toResponse(definition);
    }

    @Transactional
    public CurrencyPairDefinitionCreateResponse create(CurrencyPairDefinitionCreateRequest request) {
        if (request == null) {
            throw new InvalidRequestException("request body is required");
        }

        Long baseCurrencyId = request.getBaseCurrencyId();
        Long quoteCurrencyId = request.getQuoteCurrencyId();
        if (baseCurrencyId == null) {
            throw new InvalidRequestException("baseCurrencyId is required");
        }
        if (quoteCurrencyId == null) {
            throw new InvalidRequestException("quoteCurrencyId is required");
        }
        if (baseCurrencyId.equals(quoteCurrencyId)) {
            throw new InvalidRequestException("baseCurrencyId and quoteCurrencyId must differ");
        }
        if (currencyMapper.findById(baseCurrencyId) == null) {
            throw new InvalidRequestException("baseCurrencyId does not reference an existing currency");
        }
        if (currencyMapper.findById(quoteCurrencyId) == null) {
            throw new InvalidRequestException("quoteCurrencyId does not reference an existing currency");
        }

        Integer precision = request.getPrecision();
        if (precision == null) {
            precision = DEFAULT_PRECISION;
        } else if (precision < MIN_PRECISION || precision > MAX_PRECISION) {
            throw new InvalidRequestException("precision must be between 0 and 8");
        }

        if (currencyPairDefinitionMapper.findByCurrencyPair(baseCurrencyId, quoteCurrencyId) != null) {
            throw new CurrencyPairDefinitionConflictException(baseCurrencyId, quoteCurrencyId);
        }

        CurrencyPairDefinition definition = new CurrencyPairDefinition();
        definition.setBaseCurrencyId(baseCurrencyId);
        definition.setQuoteCurrencyId(quoteCurrencyId);
        definition.setPrecision(precision);
        currencyPairDefinitionMapper.insert(definition);

        List<Brand> brands = brandMapper.findAll(null);
        for (Brand brand : brands) {
            CurrencyPair currencyPair = new CurrencyPair();
            currencyPair.setCurrencyPairDefinitionId(definition.getId());
            currencyPair.setBrandId(brand.getId());
            currencyPair.setRateType("AUTO");
            currencyPair.setRate(null);
            currencyPair.setActive(false);
            currencyPairMapper.insert(currencyPair);
        }

        CurrencyPairDefinition created = currencyPairDefinitionMapper.findById(definition.getId());
        List<CurrencyPairResponse> currencyPairs = currencyPairMapper.findByDefinitionId(definition.getId()).stream()
                .map(CurrencyPairDefinitionService::toCurrencyPairResponse)
                .toList();

        return new CurrencyPairDefinitionCreateResponse(
                created.getId(),
                created.getBaseCurrencyId(),
                created.getBaseCurrencyCode(),
                created.getQuoteCurrencyId(),
                created.getQuoteCurrencyCode(),
                created.getPrecision(),
                created.getCreatedAt(),
                created.getUpdatedAt(),
                currencyPairs);
    }

    @Transactional
    public CurrencyPairDefinitionResponse update(Long id, CurrencyPairDefinitionUpdateRequest request) {
        if (request == null) {
            throw new InvalidRequestException("request body is required");
        }

        CurrencyPairDefinition existing = currencyPairDefinitionMapper.findById(id);
        if (existing == null) {
            throw new CurrencyPairDefinitionNotFoundException(id);
        }

        Integer precision = request.getPrecision();
        if (precision == null || precision < MIN_PRECISION || precision > MAX_PRECISION) {
            throw new InvalidRequestException("precision must be between 0 and 8");
        }

        currencyPairDefinitionMapper.updatePrecision(id, precision);

        CurrencyPairDefinition updated = currencyPairDefinitionMapper.findById(id);
        return toResponse(updated);
    }

    @Transactional
    public void delete(Long id) {
        CurrencyPairDefinition existing = currencyPairDefinitionMapper.findById(id);
        if (existing == null) {
            throw new CurrencyPairDefinitionNotFoundException(id);
        }

        List<String> activeBrandCodes = currencyPairMapper.findActiveBrandCodesByDefinitionId(id);
        if (!activeBrandCodes.isEmpty()) {
            throw new ActiveCurrencyPairsExistException(activeBrandCodes);
        }

        currencyPairDefinitionMapper.deleteById(id);
    }

    private static CurrencyPairDefinitionResponse toResponse(CurrencyPairDefinition definition) {
        return new CurrencyPairDefinitionResponse(
                definition.getId(),
                definition.getBaseCurrencyId(),
                definition.getBaseCurrencyCode(),
                definition.getQuoteCurrencyId(),
                definition.getQuoteCurrencyCode(),
                definition.getPrecision(),
                definition.getCreatedAt(),
                definition.getUpdatedAt());
    }

    private static CurrencyPairResponse toCurrencyPairResponse(CurrencyPair currencyPair) {
        return new CurrencyPairResponse(
                currencyPair.getId(),
                currencyPair.getCurrencyPairDefinitionId(),
                currencyPair.getBaseCurrencyCode(),
                currencyPair.getQuoteCurrencyCode(),
                currencyPair.getBrandId(),
                currencyPair.getBrandCode(),
                currencyPair.getRateType(),
                currencyPair.getRate(),
                currencyPair.getActive(),
                currencyPair.getCreatedAt(),
                currencyPair.getUpdatedAt());
    }
}
