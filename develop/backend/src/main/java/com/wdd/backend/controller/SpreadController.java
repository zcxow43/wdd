package com.wdd.backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wdd.backend.dto.EffectiveSpreadResponse;
import com.wdd.backend.service.EffectiveSpreadService;

@RestController
public class SpreadController {

    private final EffectiveSpreadService effectiveSpreadService;

    public SpreadController(EffectiveSpreadService effectiveSpreadService) {
        this.effectiveSpreadService = effectiveSpreadService;
    }

    @GetMapping("/api/spreads/effective")
    public List<EffectiveSpreadResponse> getEffectiveSpreads(@RequestParam(required = false) Long brandId) {
        return effectiveSpreadService.findByBrandId(brandId);
    }
}
