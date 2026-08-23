package com.wdd.backend.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.wdd.backend.dto.SpreadGroup;

/**
 * Mapper for the {@code spread_group} table — a brand's named group spreads
 * (群組點差). Membership itself lives on {@code currency_pair.spread_group_id}
 * and is handled through {@link CurrencyPairMapper}.
 */
@Mapper
public interface SpreadGroupMapper {

    List<SpreadGroup> findAll(@Param("brandId") Long brandId);

    SpreadGroup findById(@Param("id") Long id);

    SpreadGroup findByBrandAndName(@Param("brandId") Long brandId, @Param("name") String name);

    int insert(SpreadGroup spreadGroup);

    int update(SpreadGroup spreadGroup);

    int deleteById(@Param("id") Long id);

}
