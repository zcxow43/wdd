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

import com.wdd.backend.dto.CurrencyPairDefinitionCreateRequest;
import com.wdd.backend.dto.CurrencyPairDefinitionCreateResponse;
import com.wdd.backend.dto.CurrencyPairDefinitionResponse;
import com.wdd.backend.dto.CurrencyPairDefinitionUpdateRequest;
import com.wdd.backend.service.CurrencyPairDefinitionService;

@RestController
public class CurrencyPairDefinitionController {

    private final CurrencyPairDefinitionService currencyPairDefinitionService;

    public CurrencyPairDefinitionController(CurrencyPairDefinitionService currencyPairDefinitionService) {
        this.currencyPairDefinitionService = currencyPairDefinitionService;
    }

    @GetMapping("/api/currency-pair-definitions")
    public List<CurrencyPairDefinitionResponse> listCurrencyPairDefinitions() {
        return currencyPairDefinitionService.findAll();
    }

    @GetMapping("/api/currency-pair-definitions/{id}")
    public CurrencyPairDefinitionResponse getCurrencyPairDefinition(@PathVariable Long id) {
        return currencyPairDefinitionService.findById(id);
    }

    @PostMapping("/api/currency-pair-definitions")
    public ResponseEntity<CurrencyPairDefinitionCreateResponse> createCurrencyPairDefinition(
            @RequestBody CurrencyPairDefinitionCreateRequest request) {
        CurrencyPairDefinitionCreateResponse created = currencyPairDefinitionService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/api/currency-pair-definitions/{id}")
    public CurrencyPairDefinitionResponse updateCurrencyPairDefinition(@PathVariable Long id,
            @RequestBody CurrencyPairDefinitionUpdateRequest request) {
        return currencyPairDefinitionService.update(id, request);
    }

    @DeleteMapping("/api/currency-pair-definitions/{id}")
    public ResponseEntity<Void> deleteCurrencyPairDefinition(@PathVariable Long id) {
        currencyPairDefinitionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
