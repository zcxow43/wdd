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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wdd.backend.dto.AuditPendingResponse;
import com.wdd.backend.dto.CurrencyPairCreateRequest;
import com.wdd.backend.dto.CurrencyPairResponse;
import com.wdd.backend.dto.CurrencyPairUpdateRequest;
import com.wdd.backend.service.CurrencyPairService;

@RestController
public class CurrencyPairController {

    private final CurrencyPairService currencyPairService;

    public CurrencyPairController(CurrencyPairService currencyPairService) {
        this.currencyPairService = currencyPairService;
    }

    @GetMapping("/api/currency-pairs")
    public List<CurrencyPairResponse> listCurrencyPairs(
            @RequestParam(required = false) Long currencyPairDefinitionId,
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) Boolean active) {
        return currencyPairService.findAll(currencyPairDefinitionId, brandId, active);
    }

    @GetMapping("/api/currency-pairs/{id}")
    public CurrencyPairResponse getCurrencyPair(@PathVariable Long id) {
        return currencyPairService.findById(id);
    }

    @PostMapping("/api/currency-pairs")
    public ResponseEntity<AuditPendingResponse> createCurrencyPair(@RequestBody CurrencyPairCreateRequest request,
            @RequestHeader(value = "X-Actor", required = false) String actor) {
        AuditPendingResponse pending = currencyPairService.create(request, actor);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(pending);
    }

    @PutMapping("/api/currency-pairs/{id}")
    public ResponseEntity<AuditPendingResponse> updateCurrencyPair(@PathVariable Long id,
            @RequestBody CurrencyPairUpdateRequest request,
            @RequestHeader(value = "X-Actor", required = false) String actor) {
        AuditPendingResponse pending = currencyPairService.update(id, request, actor);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(pending);
    }

    @DeleteMapping("/api/currency-pairs/{id}")
    public ResponseEntity<AuditPendingResponse> deleteCurrencyPair(@PathVariable Long id,
            @RequestHeader(value = "X-Actor", required = false) String actor) {
        AuditPendingResponse pending = currencyPairService.delete(id, actor);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(pending);
    }
}
