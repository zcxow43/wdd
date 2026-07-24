package pl.piomin.services.backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import pl.piomin.services.backend.dto.BrandResponse;
import pl.piomin.services.backend.dto.BrandUpdateRequest;
import pl.piomin.services.backend.service.BrandService;

@RestController
@RequestMapping("/api/brands")
public class BrandController {

    private final BrandService brandService;

    public BrandController(BrandService brandService) {
        this.brandService = brandService;
    }

    @GetMapping
    public List<BrandResponse> list(@RequestParam(required = false) Boolean active) {
        return brandService.list(active).stream()
                .map(BrandResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public BrandResponse getById(@PathVariable Long id) {
        return BrandResponse.from(brandService.getById(id));
    }

    @PutMapping("/{id}")
    public BrandResponse update(@PathVariable Long id, @Valid @RequestBody BrandUpdateRequest request) {
        return BrandResponse.from(brandService.updateActive(id, request));
    }
}
