package com.wdd.backend.mapper;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.wdd.backend.model.Brand;

@Mapper
public interface BrandMapper {

    List<Brand> findAll(@Param("active") Boolean active);

    Optional<Brand> findById(Long id);

    int update(Brand brand);
}
