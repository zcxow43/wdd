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
import pl.piomin.services.backend.dto.CurrencyPairCreateRequest;
import pl.piomin.services.backend.dto.CurrencyPairResponse;
import pl.piomin.services.backend.dto.CurrencyPairUpdateRequest;
import pl.piomin.services.backend.service.CurrencyPairService;

@RestController
@RequestMapping("/api/currency-pairs")
public class CurrencyPairController {

    private final CurrencyPairService currencyPairService;

    public CurrencyPairController(CurrencyPairService currencyPairService) {
        this.currencyPairService = currencyPairService;
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

    @PostMapping
    public ResponseEntity<CurrencyPairResponse> create(@Valid @RequestBody CurrencyPairCreateRequest request) {
        CurrencyPairResponse response = CurrencyPairResponse.from(currencyPairService.create(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public CurrencyPairResponse update(@PathVariable Long id, @Valid @RequestBody CurrencyPairUpdateRequest request) {
        return CurrencyPairResponse.from(currencyPairService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        currencyPairService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
