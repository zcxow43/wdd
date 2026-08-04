package com.wdd.backend.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wdd.backend.audit.AuditActionType;
import com.wdd.backend.audit.AuditRequestResponse;
import com.wdd.backend.audit.AuditService;
import com.wdd.backend.dto.CurrencyPairDeleteRequest;
import com.wdd.backend.dto.CurrencyPairResponse;
import com.wdd.backend.dto.CurrencyPairUpdateRequest;
import com.wdd.backend.service.CurrencyPairAuditHandler;
import com.wdd.backend.service.CurrencyPairService;

import jakarta.validation.Valid;

/**
 * There is no {@code POST /api/currency-pairs} — a brand's {@code currency_pair} row can only
 * ever come into existence via {@code CurrencyPairDefinitionService}'s per-brand fan-out
 * (specs/backend/currency-pair-definition.md). {@code PUT}/{@code DELETE} no longer mutate
 * {@code currency_pair} directly; they submit a {@code CURRENCY_PAIR} audit request through
 * {@link AuditService} (handled by {@link CurrencyPairAuditHandler}) and return
 * {@code 202 Accepted} with the resulting {@link AuditRequestResponse}. {@code GET} is
 * unaffected — it keeps reading live, already-approved rows directly.
 */
@RestController
@RequestMapping("/api/currency-pairs")
public class CurrencyPairController {

    private static final String ENTITY_TYPE = "CURRENCY_PAIR";

    private final CurrencyPairService currencyPairService;
    private final AuditService auditService;

    public CurrencyPairController(CurrencyPairService currencyPairService, AuditService auditService) {
        this.currencyPairService = currencyPairService;
        this.auditService = auditService;
    }

    @GetMapping
    public List<CurrencyPairResponse> list(@RequestParam(required = false) Long brandId,
            @RequestParam(required = false) Boolean active) {
        return currencyPairService.list(brandId, active);
    }

    @GetMapping("/{id}")
    public CurrencyPairResponse getById(@PathVariable Long id) {
        return currencyPairService.getById(id);
    }

    @PutMapping("/{id}")
    public ResponseEntity<AuditRequestResponse> update(@PathVariable Long id,
            @Valid @RequestBody CurrencyPairUpdateRequest request) {
        CurrencyPairResponse current = currencyPairService.getById(id);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("brandId", request.getBrandId() != null ? request.getBrandId() : current.getBrandId());
        after.put("baseCurrencyId",
                request.getBaseCurrencyId() != null ? request.getBaseCurrencyId() : current.getBaseCurrencyId());
        after.put("quoteCurrencyId",
                request.getQuoteCurrencyId() != null ? request.getQuoteCurrencyId() : current.getQuoteCurrencyId());
        after.put("rateType", request.getRateType() != null ? request.getRateType() : current.getRateType());
        after.put("rate", request.getRate() != null ? request.getRate() : current.getRate());
        after.put("active", request.getActive() != null ? request.getActive() : current.getActive());

        AuditRequestResponse response = auditService.submit(ENTITY_TYPE, AuditActionType.UPDATE, id, after,
                request.getRequestedBy());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<AuditRequestResponse> delete(@PathVariable Long id,
            @RequestBody(required = false) CurrencyPairDeleteRequest request) {
        String requestedBy = request != null ? request.getRequestedBy() : null;
        AuditRequestResponse response = auditService.submit(ENTITY_TYPE, AuditActionType.DELETE, id, null,
                requestedBy);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
