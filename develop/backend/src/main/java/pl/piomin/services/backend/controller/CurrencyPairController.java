package pl.piomin.services.backend.controller;

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

import jakarta.validation.Valid;
import pl.piomin.services.backend.audit.AuditActionType;
import pl.piomin.services.backend.audit.AuditRequest;
import pl.piomin.services.backend.audit.AuditRequestResponse;
import pl.piomin.services.backend.audit.AuditService;
import pl.piomin.services.backend.dto.CurrencyPairDeleteRequest;
import pl.piomin.services.backend.dto.CurrencyPairResponse;
import pl.piomin.services.backend.dto.CurrencyPairUpdateRequest;
import pl.piomin.services.backend.model.CurrencyPair;
import pl.piomin.services.backend.service.CurrencyPairAuditHandler;
import pl.piomin.services.backend.service.CurrencyPairService;

/**
 * GET endpoints keep reading live, already-approved rows from
 * {@code currency_pair} directly and are unaffected by the audit-approval
 * delta. There is no {@code POST} route - a brand's {@code currency_pair}
 * row can only come into existence via a global currency-pair-definition's
 * fan-out (specs/backend/currency-pair-definition.md), never directly.
 * PUT/DELETE no longer mutate {@code currency_pair} directly - they submit a
 * change request through the generic audit module (specs/backend/audit.md)
 * via {@link AuditService#submit} and return {@code 202 Accepted} with the
 * resulting {@link AuditRequestResponse}.
 */
@RestController
@RequestMapping("/api/currency-pairs")
public class CurrencyPairController {

    private final CurrencyPairService currencyPairService;
    private final AuditService auditService;

    public CurrencyPairController(CurrencyPairService currencyPairService, AuditService auditService) {
        this.currencyPairService = currencyPairService;
        this.auditService = auditService;
    }

    @GetMapping
    public List<CurrencyPairResponse> list(@RequestParam(required = false) Long brandId,
                                            @RequestParam(required = false) Boolean active) {
        return currencyPairService.list(brandId, active).stream()
                .map(CurrencyPairResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public CurrencyPairResponse getById(@PathVariable Long id) {
        return CurrencyPairResponse.from(currencyPairService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AuditRequestResponse> update(@PathVariable Long id,
                                                         @Valid @RequestBody CurrencyPairUpdateRequest request) {
        // Merge the partial request onto the pair's current values so the
        // proposed "after" snapshot submitted for approval is self-contained,
        // per specs/backend/currency-pair-approval.md.
        CurrencyPair existing = currencyPairService.getById(id);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("brandId", request.getBrandId() != null ? request.getBrandId() : existing.getBrandId());
        after.put("baseCurrencyId", request.getBaseCurrencyId() != null
                ? request.getBaseCurrencyId() : existing.getBaseCurrencyId());
        after.put("quoteCurrencyId", request.getQuoteCurrencyId() != null
                ? request.getQuoteCurrencyId() : existing.getQuoteCurrencyId());
        after.put("rateType", request.getRateType() != null ? request.getRateType() : existing.getRateType());
        after.put("rate", request.getRate() != null ? request.getRate() : existing.getRate());
        after.put("active", request.getActive() != null ? request.getActive() : existing.getActive());

        AuditRequest auditRequest = auditService.submit(CurrencyPairAuditHandler.ENTITY_TYPE, AuditActionType.UPDATE,
                id, after, request.getRequestedBy());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(AuditRequestResponse.from(auditRequest));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<AuditRequestResponse> delete(@PathVariable Long id,
            @RequestBody(required = false) CurrencyPairDeleteRequest request) {
        String requestedBy = request != null ? request.getRequestedBy() : null;
        AuditRequest auditRequest = auditService.submit(CurrencyPairAuditHandler.ENTITY_TYPE, AuditActionType.DELETE,
                id, null, requestedBy);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(AuditRequestResponse.from(auditRequest));
    }
}
