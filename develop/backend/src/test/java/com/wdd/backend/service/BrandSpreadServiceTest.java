package com.wdd.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wdd.backend.dto.AuditPendingResponse;
import com.wdd.backend.dto.AuditRequest;
import com.wdd.backend.dto.Brand;
import com.wdd.backend.dto.BrandSpread;
import com.wdd.backend.dto.BrandSpreadResponse;
import com.wdd.backend.dto.BrandSpreadUpdateRequest;
import com.wdd.backend.exception.BrandNotFoundException;
import com.wdd.backend.exception.InvalidRequestException;
import com.wdd.backend.mapper.BrandMapper;
import com.wdd.backend.mapper.BrandSpreadMapper;

class BrandSpreadServiceTest {

    private BrandSpreadMapper brandSpreadMapper;
    private BrandMapper brandMapper;
    private AuditService auditService;
    private BrandSpreadService service;

    @BeforeEach
    void setUp() {
        brandSpreadMapper = mock(BrandSpreadMapper.class);
        brandMapper = mock(BrandMapper.class);
        auditService = mock(AuditService.class);
        service = new BrandSpreadService(brandSpreadMapper, brandMapper, auditService);

        when(auditService.submit(anyString(), anyString(), any(), any(), anyString(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    AuditRequest request = new AuditRequest();
                    request.setId(999L);
                    request.setEntityType(invocation.getArgument(0));
                    request.setActionType(invocation.getArgument(1));
                    request.setEntityId(invocation.getArgument(2));
                    request.setBrandId(invocation.getArgument(3));
                    request.setSummary(invocation.getArgument(4));
                    request.setBeforeData(invocation.getArgument(5));
                    request.setAfterData(invocation.getArgument(6));
                    request.setStatus("PENDING");
                    return request;
                });
    }

    private static Brand sampleBrand(Long id, String code) {
        Brand brand = new Brand();
        brand.setId(id);
        brand.setCode(code);
        brand.setName(code);
        brand.setActive(true);
        return brand;
    }

    private static BrandSpread sampleSpread(Long brandId, String brandCode, BigDecimal deposit,
            BigDecimal withdrawal) {
        BrandSpread spread = new BrandSpread();
        spread.setId(1L);
        spread.setBrandId(brandId);
        spread.setBrandCode(brandCode);
        spread.setDepositSpread(deposit);
        spread.setWithdrawalSpread(withdrawal);
        spread.setCreatedAt(LocalDateTime.now());
        spread.setUpdatedAt(LocalDateTime.now());
        return spread;
    }

    @Test
    void findByBrandIdThrowsWhenBrandMissing() {
        when(brandMapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.findByBrandId(999L))
                .isInstanceOf(BrandNotFoundException.class);
    }

    @Test
    void findByBrandIdAutoCreatesZeroRowWhenMissing() {
        when(brandMapper.findById(1L)).thenReturn(sampleBrand(1L, "au"));
        when(brandSpreadMapper.findByBrandId(1L))
                .thenReturn(null)
                .thenReturn(sampleSpread(1L, "au", BigDecimal.ZERO, BigDecimal.ZERO));

        BrandSpreadResponse response = service.findByBrandId(1L);

        verify(brandSpreadMapper).insertZero(1L);
        assertThat(response.getDepositSpread()).isEqualByComparingTo("0");
        assertThat(response.getWithdrawalSpread()).isEqualByComparingTo("0");
    }

    @Test
    void findByBrandIdReturnsExistingRowWithoutInserting() {
        when(brandMapper.findById(1L)).thenReturn(sampleBrand(1L, "au"));
        when(brandSpreadMapper.findByBrandId(1L))
                .thenReturn(sampleSpread(1L, "au", new BigDecimal("0.00050000"), new BigDecimal("0.00080000")));

        BrandSpreadResponse response = service.findByBrandId(1L);

        verify(brandSpreadMapper, never()).insertZero(any());
        assertThat(response.getBrandCode()).isEqualTo("au");
        assertThat(response.getDepositSpread()).isEqualByComparingTo("0.0005");
    }

    @Test
    void updateRejectsMissingDepositSpread() {
        when(brandMapper.findById(1L)).thenReturn(sampleBrand(1L, "au"));
        BrandSpreadUpdateRequest request = new BrandSpreadUpdateRequest(null, new BigDecimal("0.0001"));

        assertThatThrownBy(() -> service.update(1L, request, null))
                .isInstanceOf(InvalidRequestException.class);
        verify(auditService, never()).submit(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void updateRejectsNegativeSpread() {
        when(brandMapper.findById(1L)).thenReturn(sampleBrand(1L, "au"));
        BrandSpreadUpdateRequest request = new BrandSpreadUpdateRequest(new BigDecimal("-0.0001"),
                new BigDecimal("0.0001"));

        assertThatThrownBy(() -> service.update(1L, request, null))
                .isInstanceOf(InvalidRequestException.class);
        verify(auditService, never()).submit(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void updateRejectsSpreadExceedingEightDecimalPlaces() {
        when(brandMapper.findById(1L)).thenReturn(sampleBrand(1L, "au"));
        BrandSpreadUpdateRequest request = new BrandSpreadUpdateRequest(new BigDecimal("0.000000001"),
                new BigDecimal("0.0001"));

        assertThatThrownBy(() -> service.update(1L, request, null))
                .isInstanceOf(InvalidRequestException.class);
        verify(auditService, never()).submit(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void updateThrowsWhenBrandMissing() {
        when(brandMapper.findById(999L)).thenReturn(null);
        BrandSpreadUpdateRequest request = new BrandSpreadUpdateRequest(new BigDecimal("0.0001"),
                new BigDecimal("0.0002"));

        assertThatThrownBy(() -> service.update(999L, request, null))
                .isInstanceOf(BrandNotFoundException.class);
        verify(auditService, never()).submit(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void updateSubmitsAuditRequestWithZeroBeforeDataWhenRowMissingAndDoesNotWrite() {
        when(brandMapper.findById(1L)).thenReturn(sampleBrand(1L, "au"));
        when(brandSpreadMapper.findByBrandId(1L)).thenReturn(null);
        BrandSpreadUpdateRequest request = new BrandSpreadUpdateRequest(new BigDecimal("0.0001"),
                new BigDecimal("0.0002"));

        AuditPendingResponse response = service.update(1L, request, "alice");

        assertThat(response.getAuditRequestId()).isEqualTo(999L);
        assertThat(response.getStatus()).isEqualTo("PENDING");
        assertThat(response.getEntityType()).isEqualTo("BRAND_SPREAD");
        assertThat(response.getActionType()).isEqualTo("UPDATE");
        assertThat(response.getEntityId()).isEqualTo(1L);

        org.mockito.ArgumentCaptor<Object> beforeCaptor = org.mockito.ArgumentCaptor.forClass(Object.class);
        org.mockito.ArgumentCaptor<Object> afterCaptor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(auditService).submit(eq("BRAND_SPREAD"), eq("UPDATE"), eq(1L), eq(1L), anyString(),
                beforeCaptor.capture(), afterCaptor.capture(), eq("alice"));

        @SuppressWarnings("unchecked")
        Map<String, Object> before = (Map<String, Object>) beforeCaptor.getValue();
        @SuppressWarnings("unchecked")
        Map<String, Object> after = (Map<String, Object>) afterCaptor.getValue();
        assertThat((BigDecimal) before.get("depositSpread")).isEqualByComparingTo("0");
        assertThat((BigDecimal) after.get("depositSpread")).isEqualByComparingTo("0.0001");
        assertThat((BigDecimal) after.get("withdrawalSpread")).isEqualByComparingTo("0.0002");

        verify(brandSpreadMapper, never()).insertZero(any());
        verify(brandSpreadMapper, never()).update(any(), any(), any());
    }

    @Test
    void updateSubmitsAuditRequestWithCurrentValuesAsBeforeDataWhenRowExists() {
        when(brandMapper.findById(1L)).thenReturn(sampleBrand(1L, "au"));
        when(brandSpreadMapper.findByBrandId(1L))
                .thenReturn(sampleSpread(1L, "au", new BigDecimal("0.0003"), new BigDecimal("0.0004")));
        BrandSpreadUpdateRequest request = new BrandSpreadUpdateRequest(new BigDecimal("0.0005"),
                new BigDecimal("0.0008"));

        service.update(1L, request, null);

        org.mockito.ArgumentCaptor<Object> beforeCaptor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(auditService).submit(eq("BRAND_SPREAD"), eq("UPDATE"), eq(1L), eq(1L), anyString(),
                beforeCaptor.capture(), any(), isNull());
        @SuppressWarnings("unchecked")
        Map<String, Object> before = (Map<String, Object>) beforeCaptor.getValue();
        assertThat((BigDecimal) before.get("depositSpread")).isEqualByComparingTo("0.0003");
        assertThat((BigDecimal) before.get("withdrawalSpread")).isEqualByComparingTo("0.0004");
        verify(brandSpreadMapper, never()).update(any(), any(), any());
    }
}
