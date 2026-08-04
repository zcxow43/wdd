package com.wdd.backend.mapper;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.wdd.backend.model.SpreadGroup;

@Mapper
public interface SpreadGroupMapper {

    List<SpreadGroup> findAll(@Param("brandId") Long brandId);

    Optional<SpreadGroup> findById(Long id);

    /**
     * Name-uniqueness-within-brand check, optionally excluding a given row id — pass
     * {@code excludeId = null} on create, or the row's own id on update.
     */
    Optional<SpreadGroup> findByBrandAndName(@Param("brandId") Long brandId, @Param("name") String name,
            @Param("excludeId") Long excludeId);

    int insert(SpreadGroup spreadGroup);

    int update(SpreadGroup spreadGroup);

    int deleteById(Long id);
}
