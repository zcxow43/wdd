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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wdd.backend.dto.CurrencyPairDefinitionCreateRequest;
import com.wdd.backend.dto.CurrencyPairDefinitionResponse;
import com.wdd.backend.dto.CurrencyPairDefinitionUpdateRequest;
import com.wdd.backend.service.CurrencyPairDefinitionService;

import jakarta.validation.Valid;

/**
 * This feature applies immediately and does not go through the audit-approval workflow —
 * POST/PUT/DELETE mutate directly (specs/backend/currency-pair-definition.md). No change
 * whatsoever is made to CurrencyPairController/CurrencyPairService/the existing audit-approval
 * workflow for the per-brand currency_pair table.
 */
@RestController
@RequestMapping("/api/currency-pair-definitions")
public class CurrencyPairDefinitionController {

    private final CurrencyPairDefinitionService currencyPairDefinitionService;

    public CurrencyPairDefinitionController(CurrencyPairDefinitionService currencyPairDefinitionService) {
        this.currencyPairDefinitionService = currencyPairDefinitionService;
    }

    @GetMapping
    public List<CurrencyPairDefinitionResponse> list(@RequestParam(required = false) Long baseCurrencyId,
            @RequestParam(required = false) Long quoteCurrencyId) {
        return currencyPairDefinitionService.list(baseCurrencyId, quoteCurrencyId);
    }

    @GetMapping("/{id}")
    public CurrencyPairDefinitionResponse getById(@PathVariable Long id) {
        return currencyPairDefinitionService.getById(id);
    }

    @PostMapping
    public ResponseEntity<CurrencyPairDefinitionResponse> create(
            @Valid @RequestBody CurrencyPairDefinitionCreateRequest request) {
        CurrencyPairDefinitionResponse created = currencyPairDefinitionService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public CurrencyPairDefinitionResponse update(@PathVariable Long id,
            @Valid @RequestBody CurrencyPairDefinitionUpdateRequest request) {
        return currencyPairDefinitionService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        currencyPairDefinitionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
