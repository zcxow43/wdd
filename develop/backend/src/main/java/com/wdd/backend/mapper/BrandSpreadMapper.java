package com.wdd.backend.mapper;

import java.math.BigDecimal;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.wdd.backend.dto.BrandSpread;

/**
 * Mapper for the {@code brand_spread} table — a brand's default spread
 * (預設點差).
 */
@Mapper
public interface BrandSpreadMapper {

    List<BrandSpread> findAll(@Param("brandId") Long brandId);

    BrandSpread findByBrandId(@Param("brandId") Long brandId);

    int insertZero(@Param("brandId") Long brandId);

    int update(@Param("brandId") Long brandId, @Param("depositSpread") BigDecimal depositSpread,
            @Param("withdrawalSpread") BigDecimal withdrawalSpread);

}
