package com.wdd.backend.mapper;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.wdd.backend.model.CurrencyPairDefinition;

@Mapper
public interface CurrencyPairDefinitionMapper {

    List<CurrencyPairDefinition> findAll(@Param("baseCurrencyId") Long baseCurrencyId,
            @Param("quoteCurrencyId") Long quoteCurrencyId);

    Optional<CurrencyPairDefinition> findById(Long id);

    /**
     * Direction-independent pre-check mirroring the DB's pair_key_low/pair_key_high unique index
     * (specs/dba/currency-pair-definition.md) — matches an existing definition for this
     * (base, quote) pair in EITHER direction, i.e. (baseCurrencyId, quoteCurrencyId) or
     * (quoteCurrencyId, baseCurrencyId).
     */
    Optional<CurrencyPairDefinition> findByEitherDirection(@Param("baseCurrencyId") Long baseCurrencyId,
            @Param("quoteCurrencyId") Long quoteCurrencyId);

    int insert(CurrencyPairDefinition definition);

    int update(CurrencyPairDefinition definition);

    int deleteById(Long id);
}
