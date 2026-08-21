package com.wdd.backend.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.wdd.backend.dto.CurrencyPairDefinition;

@Mapper
public interface CurrencyPairDefinitionMapper {

    List<CurrencyPairDefinition> findAll();

    CurrencyPairDefinition findById(@Param("id") Long id);

    CurrencyPairDefinition findByCurrencyPair(@Param("baseCurrencyId") Long baseCurrencyId,
            @Param("quoteCurrencyId") Long quoteCurrencyId);

    int insert(CurrencyPairDefinition definition);

    int updatePrecision(@Param("id") Long id, @Param("precision") Integer precision);

    int deleteById(@Param("id") Long id);

}
