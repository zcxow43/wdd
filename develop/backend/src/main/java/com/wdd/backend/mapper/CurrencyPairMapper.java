package com.wdd.backend.mapper;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.wdd.backend.model.CurrencyPair;

@Mapper
public interface CurrencyPairMapper {

    List<CurrencyPair> findAll(@Param("brandId") Long brandId, @Param("active") Boolean active);

    Optional<CurrencyPair> findById(Long id);

    /**
     * Uniqueness check for (brandId, baseCurrencyId, quoteCurrencyId), optionally excluding a
     * given row id — pass {@code excludeId = null} on create, or the current row's id on update
     * (to allow "no-op" updates that don't actually change the triple).
     */
    Optional<CurrencyPair> findByBrandBaseQuote(@Param("brandId") Long brandId,
            @Param("baseCurrencyId") Long baseCurrencyId,
            @Param("quoteCurrencyId") Long quoteCurrencyId,
            @Param("excludeId") Long excludeId);

    int insert(CurrencyPair pair);

    int update(CurrencyPair pair);

    int deleteById(Long id);

    /**
     * Used by CurrencyService.delete's referential-integrity guard — true if the given currency
     * id is still referenced as either base_currency_id or quote_currency_id by any pair.
     */
    boolean existsByCurrencyId(Long id);

    /**
     * Used by CurrencyPairDefinitionService.delete's deletion guard
     * (specs/backend/currency-pair-definition.md) — every currency_pair row (enriched with
     * brandCode, reusing the existing joined-query shape) matching this (base, quote) direction
     * across all brands where active = true. Purely additive — no existing method's behavior
     * changes.
     */
    List<CurrencyPair> findActiveByBaseQuote(@Param("baseCurrencyId") Long baseCurrencyId,
            @Param("quoteCurrencyId") Long quoteCurrencyId);
}
