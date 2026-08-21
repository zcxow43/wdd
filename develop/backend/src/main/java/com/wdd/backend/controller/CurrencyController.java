package com.wdd.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.wdd.backend.dto.CurrencyCreateRequest;
import com.wdd.backend.dto.CurrencyResponse;
import com.wdd.backend.dto.CurrencyUpdateRequest;
import com.wdd.backend.service.CurrencyService;

@RestController
public class CurrencyController {

    private final CurrencyService currencyService;

    public CurrencyController(CurrencyService currencyService) {
        this.currencyService = currencyService;
    }

    @GetMapping("/api/currencies")
    public List<CurrencyResponse> listCurrencies() {
        return currencyService.findAll();
    }

    @GetMapping("/api/currencies/{id}")
    public CurrencyResponse getCurrency(@PathVariable Long id) {
        return currencyService.findById(id);
    }

    @PostMapping("/api/currencies")
    public ResponseEntity<CurrencyResponse> createCurrency(@RequestBody CurrencyCreateRequest request) {
        CurrencyResponse created = currencyService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/api/currencies/{id}")
    public CurrencyResponse updateCurrency(@PathVariable Long id, @RequestBody CurrencyUpdateRequest request) {
        return currencyService.update(id, request);
    }

    @DeleteMapping("/api/currencies/{id}")
    public ResponseEntity<Void> deleteCurrency(@PathVariable Long id) {
        currencyService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
