package com.wdd.backend.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wdd.backend.dto.CurrencyPair;
import com.wdd.backend.dto.CurrencyPairCreateRequest;
import com.wdd.backend.dto.CurrencyPairDefinition;
import com.wdd.backend.dto.CurrencyPairResponse;
import com.wdd.backend.dto.CurrencyPairUpdateRequest;
import com.wdd.backend.exception.CurrencyPairConflictException;
import com.wdd.backend.exception.CurrencyPairNotFoundException;
import com.wdd.backend.exception.InvalidRequestException;
import com.wdd.backend.mapper.BrandMapper;
import com.wdd.backend.mapper.CurrencyPairDefinitionMapper;
import com.wdd.backend.mapper.CurrencyPairMapper;

/**
 * Brand-scoped CRUD for {@code currency_pair} rows. Most rows are created by
 * {@link CurrencyPairDefinitionService}'s fan-out; this service additionally
 * supports creating/deleting individual rows directly.
 */
@Service
public class CurrencyPairService {

    private static final String RATE_TYPE_AUTO = "AUTO";
    private static final String RATE_TYPE_MANUAL = "MANUAL";

    private final CurrencyPairMapper currencyPairMapper;
    private final CurrencyPairDefinitionMapper currencyPairDefinitionMapper;
    private final BrandMapper brandMapper;

    public CurrencyPairService(CurrencyPairMapper currencyPairMapper,
            CurrencyPairDefinitionMapper currencyPairDefinitionMapper, BrandMapper brandMapper) {
        this.currencyPairMapper = currencyPairMapper;
        this.currencyPairDefinitionMapper = currencyPairDefinitionMapper;
        this.brandMapper = brandMapper;
    }

    public List<CurrencyPairResponse> findAll(Long currencyPairDefinitionId, Long brandId, Boolean active) {
        return currencyPairMapper.findAll(currencyPairDefinitionId, brandId, active).stream()
                .map(CurrencyPairService::toResponse)
                .toList();
    }

    public CurrencyPairResponse findById(Long id) {
        CurrencyPair currencyPair = currencyPairMapper.findById(id);
        if (currencyPair == null) {
            throw new CurrencyPairNotFoundException(id);
        }
        return toResponse(currencyPair);
    }

    @Transactional
    public CurrencyPairResponse create(CurrencyPairCreateRequest request) {
        if (request == null) {
            throw new InvalidRequestException("request body is required");
        }

        Long currencyPairDefinitionId = request.getCurrencyPairDefinitionId();
        Long brandId = request.getBrandId();
        if (currencyPairDefinitionId == null) {
            throw new InvalidRequestException("currencyPairDefinitionId is required");
        }
        if (brandId == null) {
            throw new InvalidRequestException("brandId is required");
        }

        CurrencyPairDefinition definition = currencyPairDefinitionMapper.findById(currencyPairDefinitionId);
        if (definition == null) {
            throw new InvalidRequestException(
                    "currencyPairDefinitionId does not reference an existing currency pair definition");
        }
        if (brandMapper.findById(brandId) == null) {
            throw new InvalidRequestException("brandId does not reference an existing brand");
        }

        if (currencyPairMapper.findByDefinitionAndBrand(currencyPairDefinitionId, brandId) != null) {
            throw new CurrencyPairConflictException(currencyPairDefinitionId, brandId);
        }

        String rateType = normalizeRateType(request.getRateType());
        BigDecimal rate = validateAndResolveRate(rateType, request.getRate(), definition.getPrecision());

        Boolean active = request.getActive();
        if (active == null) {
            active = false;
        }

        CurrencyPair currencyPair = new CurrencyPair();
        currencyPair.setCurrencyPairDefinitionId(currencyPairDefinitionId);
        currencyPair.setBrandId(brandId);
        currencyPair.setRateType(rateType);
        currencyPair.setRate(rate);
        currencyPair.setActive(active);
        currencyPairMapper.insert(currencyPair);

        CurrencyPair created = currencyPairMapper.findById(currencyPair.getId());
        return toResponse(created);
    }

    @Transactional
    public CurrencyPairResponse update(Long id, CurrencyPairUpdateRequest request) {
        if (request == null) {
            throw new InvalidRequestException("request body is required");
        }

        CurrencyPair existing = currencyPairMapper.findById(id);
        if (existing == null) {
            throw new CurrencyPairNotFoundException(id);
        }

        CurrencyPairDefinition definition =
                currencyPairDefinitionMapper.findById(existing.getCurrencyPairDefinitionId());

        String rateType = request.getRateType() != null
                ? normalizeRateType(request.getRateType())
                : existing.getRateType();
        BigDecimal requestedRate = request.getRate() != null ? request.getRate() : existing.getRate();
        BigDecimal rate = validateAndResolveRate(rateType, requestedRate, definition.getPrecision());

        Boolean active = request.getActive() != null ? request.getActive() : existing.getActive();

        CurrencyPair toUpdate = new CurrencyPair();
        toUpdate.setId(id);
        toUpdate.setRateType(rateType);
        toUpdate.setRate(rate);
        toUpdate.setActive(active);
        currencyPairMapper.update(toUpdate);

        CurrencyPair updated = currencyPairMapper.findById(id);
        return toResponse(updated);
    }

    @Transactional
    public void delete(Long id) {
        CurrencyPair existing = currencyPairMapper.findById(id);
        if (existing == null) {
            throw new CurrencyPairNotFoundException(id);
        }
        currencyPairMapper.deleteById(id);
    }

    private static String normalizeRateType(String rateType) {
        if (rateType == null) {
            return RATE_TYPE_AUTO;
        }
        if (!RATE_TYPE_AUTO.equals(rateType) && !RATE_TYPE_MANUAL.equals(rateType)) {
            throw new InvalidRequestException("rateType must be AUTO or MANUAL");
        }
        return rateType;
    }

    private static BigDecimal validateAndResolveRate(String rateType, BigDecimal rate, Integer precision) {
        if (RATE_TYPE_AUTO.equals(rateType)) {
            return null;
        }
        if (rate == null) {
            throw new InvalidRequestException("rate is required when rateType is MANUAL");
        }
        if (rate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidRequestException("rate must be greater than 0");
        }
        int scale = Math.max(rate.stripTrailingZeros().scale(), 0);
        if (precision != null && scale > precision) {
            throw new InvalidRequestException("rate must not exceed " + precision + " decimal places");
        }
        return rate;
    }

    private static CurrencyPairResponse toResponse(CurrencyPair currencyPair) {
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
