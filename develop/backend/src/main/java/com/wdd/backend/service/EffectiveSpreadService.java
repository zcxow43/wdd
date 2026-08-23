package com.wdd.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.wdd.backend.dto.EffectiveSpread;
import com.wdd.backend.dto.EffectiveSpreadResponse;
import com.wdd.backend.exception.BrandNotFoundException;
import com.wdd.backend.exception.InvalidRequestException;
import com.wdd.backend.mapper.BrandMapper;
import com.wdd.backend.mapper.CurrencyPairMapper;

/**
 * Resolves the spread that actually applies to each of a brand's currency
 * pairs — its spread group's, if assigned, otherwise the brand's default.
 * Resolution happens entirely in SQL (see {@code CurrencyPairMapper
 * #findEffectiveSpreadsByBrandId}) so no caller re-implements the fallback
 * rule.
 */
@Service
public class EffectiveSpreadService {

    private final CurrencyPairMapper currencyPairMapper;
    private final BrandMapper brandMapper;

    public EffectiveSpreadService(CurrencyPairMapper currencyPairMapper, BrandMapper brandMapper) {
        this.currencyPairMapper = currencyPairMapper;
        this.brandMapper = brandMapper;
    }

    public List<EffectiveSpreadResponse> findByBrandId(Long brandId) {
        if (brandId == null) {
            throw new InvalidRequestException("brandId is required");
        }
        if (brandMapper.findById(brandId) == null) {
            throw new BrandNotFoundException(brandId);
        }
        return currencyPairMapper.findEffectiveSpreadsByBrandId(brandId).stream()
                .map(EffectiveSpreadService::toResponse)
                .toList();
    }

    private static EffectiveSpreadResponse toResponse(EffectiveSpread spread) {
        return new EffectiveSpreadResponse(
                spread.getCurrencyPairId(),
                spread.getCurrencyPairDefinitionId(),
                spread.getBaseCurrencyCode(),
                spread.getQuoteCurrencyCode(),
                spread.getBrandId(),
                spread.getBrandCode(),
                spread.getSpreadGroupId(),
                spread.getSpreadGroupName(),
                spread.getSource(),
                spread.getDepositSpread(),
                spread.getWithdrawalSpread());
    }
}
