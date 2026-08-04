package com.wdd.backend.mapper;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.wdd.backend.model.SpreadDefault;

@Mapper
public interface SpreadDefaultMapper {

    List<SpreadDefault> findAll(@Param("brandId") Long brandId);

    Optional<SpreadDefault> findById(Long id);

    /**
     * Used by {@code SpreadGroupService.resolveEffectiveSpread} to fall back to a brand's
     * default spread when a currency pair has no group membership.
     */
    Optional<SpreadDefault> findByBrandId(Long brandId);

    /**
     * Only ever called from {@code SpreadDefaultAuditHandler.apply(...)} once an UPDATE audit
     * request has been approved — a {@code spread_default} row is never created/deleted through
     * the API (specs/dba/spread-default.md).
     */
    int update(SpreadDefault spreadDefault);
}
