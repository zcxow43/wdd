package com.wdd.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.wdd.backend.dto.BrandResponse;
import com.wdd.backend.dto.BrandUpdateRequest;
import com.wdd.backend.exception.BrandNotFoundException;
import com.wdd.backend.mapper.BrandMapper;
import com.wdd.backend.model.Brand;

@ExtendWith(MockitoExtension.class)
class BrandServiceTest {

    @Mock
    private BrandMapper brandMapper;

    private BrandService brandService;

    @BeforeEach
    void setUp() {
        brandService = new BrandService(brandMapper);
    }

    private Brand sampleBrand() {
        Brand brand = new Brand();
        brand.setId(1L);
        brand.setCode("AU");
        brand.setName("AU");
        brand.setActive(true);
        brand.setCreatedAt(LocalDateTime.now());
        brand.setUpdatedAt(LocalDateTime.now());
        return brand;
    }

    @Test
    void list_returnsAllBrandsWhenNoFilter() {
        when(brandMapper.findAll(isNull())).thenReturn(List.of(sampleBrand()));

        List<BrandResponse> result = brandService.list(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCode()).isEqualTo("AU");
    }

    @Test
    void list_filtersByActive() {
        when(brandMapper.findAll(eq(true))).thenReturn(List.of(sampleBrand()));

        List<BrandResponse> result = brandService.list(true);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getActive()).isTrue();
        verify(brandMapper).findAll(eq(true));
    }

    @Test
    void list_returnsEmptyListWhenNoBrands() {
        when(brandMapper.findAll(any())).thenReturn(List.of());

        List<BrandResponse> result = brandService.list(false);

        assertThat(result).isEmpty();
    }

    @Test
    void getById_returnsBrandWhenFound() {
        when(brandMapper.findById(1L)).thenReturn(Optional.of(sampleBrand()));

        BrandResponse result = brandService.getById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getCode()).isEqualTo("AU");
    }

    @Test
    void getById_throwsNotFoundWhenMissing() {
        when(brandMapper.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> brandService.getById(999L))
                .isInstanceOf(BrandNotFoundException.class);
    }

    @Test
    void updateActive_disablesAndReturnsBrand() {
        Brand existing = sampleBrand();
        Brand disabled = sampleBrand();
        disabled.setActive(false);

        when(brandMapper.findById(1L)).thenReturn(Optional.of(existing), Optional.of(disabled));
        when(brandMapper.update(any(Brand.class))).thenReturn(1);

        BrandUpdateRequest request = new BrandUpdateRequest();
        request.setActive(false);

        BrandResponse result = brandService.updateActive(1L, request);

        assertThat(result.getActive()).isFalse();
        verify(brandMapper).update(any(Brand.class));
    }

    @Test
    void updateActive_throwsNotFoundWhenMissing() {
        when(brandMapper.findById(999L)).thenReturn(Optional.empty());

        BrandUpdateRequest request = new BrandUpdateRequest();
        request.setActive(true);

        assertThatThrownBy(() -> brandService.updateActive(999L, request))
                .isInstanceOf(BrandNotFoundException.class);

        verify(brandMapper, never()).update(any(Brand.class));
    }
}
