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
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import pl.piomin.services.backend.dto.CurrencyCreateRequest;
import pl.piomin.services.backend.dto.CurrencyResponse;
import pl.piomin.services.backend.dto.CurrencyUpdateRequest;
import pl.piomin.services.backend.service.CurrencyService;

@RestController
@RequestMapping("/api/currencies")
public class CurrencyController {

    private final CurrencyService currencyService;

    public CurrencyController(CurrencyService currencyService) {
        this.currencyService = currencyService;
    }

    @GetMapping
    public List<CurrencyResponse> list() {
        return currencyService.list().stream()
                .map(CurrencyResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public CurrencyResponse getById(@PathVariable Long id) {
        return CurrencyResponse.from(currencyService.getById(id));
    }

    @PostMapping
    public ResponseEntity<CurrencyResponse> create(@Valid @RequestBody CurrencyCreateRequest request) {
        CurrencyResponse response = CurrencyResponse.from(currencyService.create(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public CurrencyResponse update(@PathVariable Long id, @Valid @RequestBody CurrencyUpdateRequest request) {
        return CurrencyResponse.from(currencyService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        currencyService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
