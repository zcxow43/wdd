package com.wdd.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wdd.backend.dto.AuditPendingResponse;
import com.wdd.backend.dto.BrandSpreadResponse;
import com.wdd.backend.dto.BrandSpreadUpdateRequest;
import com.wdd.backend.service.BrandSpreadService;

@RestController
public class BrandSpreadController {

    private final BrandSpreadService brandSpreadService;

    public BrandSpreadController(BrandSpreadService brandSpreadService) {
        this.brandSpreadService = brandSpreadService;
    }

    @GetMapping("/api/brand-spreads")
    public List<BrandSpreadResponse> listBrandSpreads(@RequestParam(required = false) Long brandId) {
        return brandSpreadService.findAll(brandId);
    }

    @GetMapping("/api/brand-spreads/{brandId}")
    public BrandSpreadResponse getBrandSpread(@PathVariable Long brandId) {
        return brandSpreadService.findByBrandId(brandId);
    }

    @PutMapping("/api/brand-spreads/{brandId}")
    public ResponseEntity<AuditPendingResponse> updateBrandSpread(@PathVariable Long brandId,
            @RequestBody BrandSpreadUpdateRequest request,
            @RequestHeader(value = "X-Actor", required = false) String actor) {
        AuditPendingResponse pending = brandSpreadService.update(brandId, request, actor);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(pending);
    }
}
