package pl.piomin.services.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import pl.piomin.services.backend.dto.BrandUpdateRequest;
import pl.piomin.services.backend.exception.BrandNotFoundException;
import pl.piomin.services.backend.mapper.BrandMapper;
import pl.piomin.services.backend.model.Brand;

@ExtendWith(MockitoExtension.class)
class BrandServiceTest {

    @Mock
    private BrandMapper brandMapper;

    private BrandService brandService;

    @BeforeEach
    void setUp() {
        brandService = new BrandService(brandMapper);
    }

    private Brand sampleBrand(Long id, String code, boolean active) {
        Brand brand = new Brand();
        brand.setId(id);
        brand.setCode(code);
        brand.setName(code);
        brand.setActive(active);
        return brand;
    }

    @Test
    void list_returnsAllBrandsFromMapper() {
        when(brandMapper.findAll(null)).thenReturn(List.of(sampleBrand(1L, "AU", true)));

        List<Brand> result = brandService.list(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCode()).isEqualTo("AU");
    }

    @Test
    void list_filtersByActive() {
        when(brandMapper.findAll(true)).thenReturn(List.of(sampleBrand(1L, "AU", true)));

        List<Brand> result = brandService.list(true);

        assertThat(result).hasSize(1);
        verify(brandMapper).findAll(true);
    }

    @Test
    void getById_returnsBrand_whenFound() {
        when(brandMapper.findById(1L)).thenReturn(sampleBrand(1L, "AU", true));

        Brand result = brandService.getById(1L);

        assertThat(result.getCode()).isEqualTo("AU");
    }

    @Test
    void getById_throwsNotFound_whenMissing() {
        when(brandMapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> brandService.getById(999L))
                .isInstanceOf(BrandNotFoundException.class);
    }

    @Test
    void updateActive_disablesBrand_whenFound() {
        Brand existing = sampleBrand(1L, "AU", true);
        Brand updated = sampleBrand(1L, "AU", false);
        when(brandMapper.findById(1L)).thenReturn(existing).thenReturn(updated);

        BrandUpdateRequest request = new BrandUpdateRequest();
        request.setActive(false);

        Brand result = brandService.updateActive(1L, request);

        assertThat(result.getActive()).isFalse();
        verify(brandMapper).update(any(Brand.class));
    }

    @Test
    void updateActive_enablesBrand_whenFound() {
        Brand existing = sampleBrand(1L, "AU", false);
        Brand updated = sampleBrand(1L, "AU", true);
        when(brandMapper.findById(1L)).thenReturn(existing).thenReturn(updated);

        BrandUpdateRequest request = new BrandUpdateRequest();
        request.setActive(true);

        Brand result = brandService.updateActive(1L, request);

        assertThat(result.getActive()).isTrue();
    }

    @Test
    void updateActive_throwsNotFound_whenMissing() {
        when(brandMapper.findById(999L)).thenReturn(null);

        BrandUpdateRequest request = new BrandUpdateRequest();
        request.setActive(false);

        assertThatThrownBy(() -> brandService.updateActive(999L, request))
                .isInstanceOf(BrandNotFoundException.class);
        verify(brandMapper, never()).update(any(Brand.class));
    }
}
