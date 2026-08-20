package com.wdd.backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wdd.backend.dto.BrandResponse;
import com.wdd.backend.dto.BrandUpdateRequest;
import com.wdd.backend.service.BrandService;

@RestController
public class BrandController {

    private final BrandService brandService;

    public BrandController(BrandService brandService) {
        this.brandService = brandService;
    }

    @GetMapping("/api/brands")
    public List<BrandResponse> listBrands(@RequestParam(required = false) Boolean active) {
        return brandService.findAll(active);
    }

    @GetMapping("/api/brands/{id}")
    public BrandResponse getBrand(@PathVariable Long id) {
        return brandService.findById(id);
    }

    @PutMapping("/api/brands/{id}")
    public BrandResponse updateBrand(@PathVariable Long id, @RequestBody BrandUpdateRequest request) {
        return brandService.updateActive(id, request);
    }
}
