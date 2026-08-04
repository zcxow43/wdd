package com.wdd.backend.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wdd.backend.dto.BrandResponse;
import com.wdd.backend.dto.BrandUpdateRequest;
import com.wdd.backend.exception.BrandNotFoundException;
import com.wdd.backend.mapper.BrandMapper;
import com.wdd.backend.model.Brand;

@Service
public class BrandService {

    private final BrandMapper brandMapper;

    public BrandService(BrandMapper brandMapper) {
        this.brandMapper = brandMapper;
    }

    public List<BrandResponse> list(Boolean active) {
        return brandMapper.findAll(active).stream()
                .map(BrandResponse::from)
                .collect(Collectors.toList());
    }

    public BrandResponse getById(Long id) {
        Brand brand = brandMapper.findById(id)
                .orElseThrow(() -> new BrandNotFoundException(id));
        return BrandResponse.from(brand);
    }

    @Transactional
    public BrandResponse updateActive(Long id, BrandUpdateRequest request) {
        Brand existing = brandMapper.findById(id)
                .orElseThrow(() -> new BrandNotFoundException(id));

        existing.setActive(request.getActive());
        brandMapper.update(existing);

        Brand updated = brandMapper.findById(id)
                .orElseThrow(() -> new BrandNotFoundException(id));
        return BrandResponse.from(updated);
    }
}
