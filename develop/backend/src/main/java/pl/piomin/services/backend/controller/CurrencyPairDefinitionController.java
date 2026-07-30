package pl.piomin.services.backend.controller;

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

import jakarta.validation.Valid;
import pl.piomin.services.backend.dto.CurrencyPairDefinitionCreateRequest;
import pl.piomin.services.backend.dto.CurrencyPairDefinitionResponse;
import pl.piomin.services.backend.dto.CurrencyPairDefinitionUpdateRequest;
import pl.piomin.services.backend.service.CurrencyPairDefinitionService;

/**
 * This feature applies immediately - unlike {@code CurrencyPairController},
 * POST/PUT/DELETE here mutate {@code currency_pair_definition} directly and
 * do not go through the generic audit-approval workflow
 * (specs/backend/currency-pair-approval.md).
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
        return currencyPairDefinitionService.list(baseCurrencyId, quoteCurrencyId).stream()
                .map(CurrencyPairDefinitionResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public CurrencyPairDefinitionResponse getById(@PathVariable Long id) {
        return CurrencyPairDefinitionResponse.from(currencyPairDefinitionService.getById(id));
    }

    @PostMapping
    public ResponseEntity<CurrencyPairDefinitionResponse> create(
            @Valid @RequestBody CurrencyPairDefinitionCreateRequest request) {
        CurrencyPairDefinitionResponse response =
                CurrencyPairDefinitionResponse.from(currencyPairDefinitionService.create(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public CurrencyPairDefinitionResponse update(@PathVariable Long id,
            @Valid @RequestBody CurrencyPairDefinitionUpdateRequest request) {
        return CurrencyPairDefinitionResponse.from(currencyPairDefinitionService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        currencyPairDefinitionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
