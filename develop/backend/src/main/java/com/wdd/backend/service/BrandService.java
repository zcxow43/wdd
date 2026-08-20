package com.wdd.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wdd.backend.dto.Brand;
import com.wdd.backend.dto.BrandResponse;
import com.wdd.backend.dto.BrandUpdateRequest;
import com.wdd.backend.exception.BrandNotFoundException;
import com.wdd.backend.exception.InvalidRequestException;
import com.wdd.backend.mapper.BrandMapper;

@Service
public class BrandService {

    private final BrandMapper brandMapper;

    public BrandService(BrandMapper brandMapper) {
        this.brandMapper = brandMapper;
    }

    public List<BrandResponse> findAll(Boolean active) {
        return brandMapper.findAll(active).stream()
                .map(BrandService::toResponse)
                .toList();
    }

    public BrandResponse findById(Long id) {
        Brand brand = brandMapper.findById(id);
        if (brand == null) {
            throw new BrandNotFoundException(id);
        }
        return toResponse(brand);
    }

    @Transactional
    public BrandResponse updateActive(Long id, BrandUpdateRequest request) {
        if (request == null || request.getActive() == null) {
            throw new InvalidRequestException("active is required");
        }

        Brand existing = brandMapper.findById(id);
        if (existing == null) {
            throw new BrandNotFoundException(id);
        }

        brandMapper.updateActive(id, request.getActive());

        Brand updated = brandMapper.findById(id);
        return toResponse(updated);
    }

    private static BrandResponse toResponse(Brand brand) {
        return new BrandResponse(
                brand.getId(),
                brand.getCode(),
                brand.getName(),
                brand.getActive(),
                brand.getCreatedAt(),
                brand.getUpdatedAt());
    }
}
