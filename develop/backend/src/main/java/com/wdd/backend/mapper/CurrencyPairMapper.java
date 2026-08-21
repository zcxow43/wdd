package com.wdd.backend.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.wdd.backend.dto.CurrencyPair;

/**
 * Mapper for the {@code currency_pair} table — backs both the currency pair
 * definition fan-out (POST)/delete-guard (active check) flows, and the
 * brand-scoped currency pair CRUD API.
 */
@Mapper
public interface CurrencyPairMapper {

    List<CurrencyPair> findByDefinitionId(@Param("definitionId") Long definitionId);

    List<String> findActiveBrandCodesByDefinitionId(@Param("definitionId") Long definitionId);

    int insert(CurrencyPair currencyPair);

    List<CurrencyPair> findAll(@Param("currencyPairDefinitionId") Long currencyPairDefinitionId,
            @Param("brandId") Long brandId, @Param("active") Boolean active);

    CurrencyPair findById(@Param("id") Long id);

    CurrencyPair findByDefinitionAndBrand(@Param("currencyPairDefinitionId") Long currencyPairDefinitionId,
            @Param("brandId") Long brandId);

    int update(CurrencyPair currencyPair);

    int deleteById(@Param("id") Long id);

}
