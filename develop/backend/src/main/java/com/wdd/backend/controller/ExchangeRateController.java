package com.wdd.backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wdd.backend.dto.ExchangeRateLatestResponse;
import com.wdd.backend.dto.ExchangeRateSyncResponse;
import com.wdd.backend.service.ExchangeRateService;

@RestController
public class ExchangeRateController {

    private final ExchangeRateService exchangeRateService;

    public ExchangeRateController(ExchangeRateService exchangeRateService) {
        this.exchangeRateService = exchangeRateService;
    }

    @GetMapping("/api/exchange-rates/latest")
    public List<ExchangeRateLatestResponse> latest(@RequestParam(required = false) Long brandId) {
        return exchangeRateService.findLatest(brandId);
    }

    @PostMapping("/api/exchange-rates/sync")
    public ExchangeRateSyncResponse sync() {
        return exchangeRateService.sync();
    }
}
