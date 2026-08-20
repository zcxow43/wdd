package com.wdd.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wdd.backend.dto.Brand;
import com.wdd.backend.dto.BrandResponse;
import com.wdd.backend.dto.BrandUpdateRequest;
import com.wdd.backend.exception.BrandNotFoundException;
import com.wdd.backend.exception.InvalidRequestException;
import com.wdd.backend.mapper.BrandMapper;

class BrandServiceTest {

    private BrandMapper brandMapper;
    private BrandService brandService;

    @BeforeEach
    void setUp() {
        brandMapper = mock(BrandMapper.class);
        brandService = new BrandService(brandMapper);
    }

    private static Brand sampleBrand(Long id, String code, Boolean active) {
        Brand brand = new Brand();
        brand.setId(id);
        brand.setCode(code);
        brand.setName(code);
        brand.setActive(active);
        brand.setCreatedAt(LocalDateTime.now());
        brand.setUpdatedAt(LocalDateTime.now());
        return brand;
    }

    @Test
    void findByIdThrowsWhenBrandMissing() {
        when(brandMapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> brandService.findById(999L))
                .isInstanceOf(BrandNotFoundException.class);
    }

    @Test
    void findByIdReturnsMappedResponse() {
        when(brandMapper.findById(1L)).thenReturn(sampleBrand(1L, "au", true));

        BrandResponse response = brandService.findById(1L);

        assertThat(response.getCode()).isEqualTo("au");
        assertThat(response.getActive()).isTrue();
    }

    @Test
    void updateActiveThrowsInvalidRequestWhenActiveMissing() {
        assertThatThrownBy(() -> brandService.updateActive(1L, new BrandUpdateRequest(null)))
                .isInstanceOf(InvalidRequestException.class);

        assertThatThrownBy(() -> brandService.updateActive(1L, null))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void updateActiveThrowsNotFoundWhenBrandMissing() {
        when(brandMapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> brandService.updateActive(999L, new BrandUpdateRequest(false)))
                .isInstanceOf(BrandNotFoundException.class);
    }

    @Test
    void updateActiveUpdatesAndReturnsRefreshedBrand() {
        when(brandMapper.findById(1L))
                .thenReturn(sampleBrand(1L, "au", true))
                .thenReturn(sampleBrand(1L, "au", false));

        BrandResponse response = brandService.updateActive(1L, new BrandUpdateRequest(false));

        verify(brandMapper, times(1)).updateActive(1L, false);
        assertThat(response.getActive()).isFalse();
    }
}
