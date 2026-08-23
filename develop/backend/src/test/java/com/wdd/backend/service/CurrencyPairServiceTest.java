package com.wdd.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
import com.wdd.backend.dto.CurrencyPair;
import com.wdd.backend.dto.CurrencyPairCreateRequest;
import com.wdd.backend.dto.CurrencyPairDefinition;
import com.wdd.backend.dto.CurrencyPairUpdateRequest;
import com.wdd.backend.exception.CurrencyPairConflictException;
import com.wdd.backend.exception.CurrencyPairNotFoundException;
import com.wdd.backend.exception.InvalidRequestException;
import com.wdd.backend.mapper.BrandMapper;
import com.wdd.backend.mapper.CurrencyPairDefinitionMapper;
import com.wdd.backend.mapper.CurrencyPairMapper;

class CurrencyPairServiceTest {

    private CurrencyPairMapper currencyPairMapper;
    private CurrencyPairDefinitionMapper currencyPairDefinitionMapper;
    private BrandMapper brandMapper;
    private AuditService auditService;
    private CurrencyPairService service;

    @BeforeEach
    void setUp() {
        currencyPairMapper = mock(CurrencyPairMapper.class);
        currencyPairDefinitionMapper = mock(CurrencyPairDefinitionMapper.class);
        brandMapper = mock(BrandMapper.class);
        auditService = mock(AuditService.class);
        service = new CurrencyPairService(currencyPairMapper, currencyPairDefinitionMapper, brandMapper,
                auditService);

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

    private static CurrencyPairDefinition sampleDefinition(Long id, Integer precision) {
        CurrencyPairDefinition definition = new CurrencyPairDefinition();
        definition.setId(id);
        definition.setBaseCurrencyId(1L);
        definition.setBaseCurrencyCode("USD");
        definition.setQuoteCurrencyId(2L);
        definition.setQuoteCurrencyCode("JPY");
        definition.setPrecision(precision);
        return definition;
    }

    private static Brand sampleBrand(Long id, String code) {
        Brand brand = new Brand();
        brand.setId(id);
        brand.setCode(code);
        brand.setName(code);
        brand.setActive(true);
        return brand;
    }

    private static CurrencyPair sampleCurrencyPair(Long id, Long definitionId, Long brandId, String rateType,
            BigDecimal rate, Boolean active) {
        CurrencyPair pair = new CurrencyPair();
        pair.setId(id);
        pair.setCurrencyPairDefinitionId(definitionId);
        pair.setBaseCurrencyCode("USD");
        pair.setQuoteCurrencyCode("JPY");
        pair.setBrandId(brandId);
        pair.setBrandCode("au");
        pair.setRateType(rateType);
        pair.setRate(rate);
        pair.setActive(active);
        pair.setCreatedAt(LocalDateTime.now());
        pair.setUpdatedAt(LocalDateTime.now());
        return pair;
    }

    @Test
    void findByIdThrowsWhenMissing() {
        when(currencyPairMapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.findById(999L))
                .isInstanceOf(CurrencyPairNotFoundException.class);
    }

    @Test
    void findByIdReturnsMappedResponse() {
        when(currencyPairMapper.findById(1L))
                .thenReturn(sampleCurrencyPair(1L, 10L, 1L, "AUTO", null, false));

        var response = service.findById(1L);

        assertThat(response.getBaseCurrencyCode()).isEqualTo("USD");
        assertThat(response.getQuoteCurrencyCode()).isEqualTo("JPY");
        assertThat(response.getBrandCode()).isEqualTo("au");
    }

    @Test
    void createRejectsMissingDefinitionId() {
        CurrencyPairCreateRequest request = new CurrencyPairCreateRequest(null, 1L, "AUTO", null, false);

        assertThatThrownBy(() -> service.create(request, null))
                .isInstanceOf(InvalidRequestException.class);
        verify(auditService, never()).submit(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void createRejectsMissingBrandId() {
        CurrencyPairCreateRequest request = new CurrencyPairCreateRequest(10L, null, "AUTO", null, false);

        assertThatThrownBy(() -> service.create(request, null))
                .isInstanceOf(InvalidRequestException.class);
        verify(auditService, never()).submit(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void createRejectsNonExistentDefinition() {
        when(currencyPairDefinitionMapper.findById(10L)).thenReturn(null);
        CurrencyPairCreateRequest request = new CurrencyPairCreateRequest(10L, 1L, "AUTO", null, false);

        assertThatThrownBy(() -> service.create(request, null))
                .isInstanceOf(InvalidRequestException.class);
        verify(auditService, never()).submit(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void createRejectsNonExistentBrand() {
        when(currencyPairDefinitionMapper.findById(10L)).thenReturn(sampleDefinition(10L, 4));
        when(brandMapper.findById(1L)).thenReturn(null);
        CurrencyPairCreateRequest request = new CurrencyPairCreateRequest(10L, 1L, "AUTO", null, false);

        assertThatThrownBy(() -> service.create(request, null))
                .isInstanceOf(InvalidRequestException.class);
        verify(auditService, never()).submit(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void createRejectsDuplicateDefinitionAndBrand() {
        when(currencyPairDefinitionMapper.findById(10L)).thenReturn(sampleDefinition(10L, 4));
        when(brandMapper.findById(1L)).thenReturn(sampleBrand(1L, "au"));
        when(currencyPairMapper.findByDefinitionAndBrand(10L, 1L))
                .thenReturn(sampleCurrencyPair(5L, 10L, 1L, "AUTO", null, false));
        CurrencyPairCreateRequest request = new CurrencyPairCreateRequest(10L, 1L, "AUTO", null, false);

        assertThatThrownBy(() -> service.create(request, null))
                .isInstanceOf(CurrencyPairConflictException.class);
        verify(auditService, never()).submit(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void createWithManualRateTypeAndNoRateThrows() {
        when(currencyPairDefinitionMapper.findById(10L)).thenReturn(sampleDefinition(10L, 4));
        when(brandMapper.findById(1L)).thenReturn(sampleBrand(1L, "au"));
        when(currencyPairMapper.findByDefinitionAndBrand(10L, 1L)).thenReturn(null);

        CurrencyPairCreateRequest request = new CurrencyPairCreateRequest(10L, 1L, "MANUAL", null, false);

        assertThatThrownBy(() -> service.create(request, null))
                .isInstanceOf(InvalidRequestException.class);
        verify(auditService, never()).submit(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void createWithManualRateTypeAndNonPositiveRateThrows() {
        when(currencyPairDefinitionMapper.findById(10L)).thenReturn(sampleDefinition(10L, 4));
        when(brandMapper.findById(1L)).thenReturn(sampleBrand(1L, "au"));
        when(currencyPairMapper.findByDefinitionAndBrand(10L, 1L)).thenReturn(null);

        CurrencyPairCreateRequest request = new CurrencyPairCreateRequest(10L, 1L, "MANUAL",
                new BigDecimal("0"), false);

        assertThatThrownBy(() -> service.create(request, null))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void createWithManualRateExceedingPrecisionThrows() {
        when(currencyPairDefinitionMapper.findById(10L)).thenReturn(sampleDefinition(10L, 2));
        when(brandMapper.findById(1L)).thenReturn(sampleBrand(1L, "au"));
        when(currencyPairMapper.findByDefinitionAndBrand(10L, 1L)).thenReturn(null);

        CurrencyPairCreateRequest request = new CurrencyPairCreateRequest(10L, 1L, "MANUAL",
                new BigDecimal("150.255"), false);

        assertThatThrownBy(() -> service.create(request, null))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void createWithAutoRateTypeSubmitsAuditRequestWithNullRate() {
        when(currencyPairDefinitionMapper.findById(10L)).thenReturn(sampleDefinition(10L, 4));
        when(brandMapper.findById(1L)).thenReturn(sampleBrand(1L, "au"));
        when(currencyPairMapper.findByDefinitionAndBrand(10L, 1L)).thenReturn(null);

        CurrencyPairCreateRequest request = new CurrencyPairCreateRequest(10L, 1L, "AUTO",
                new BigDecimal("150.25"), false);
        AuditPendingResponse response = service.create(request, "alice");

        assertThat(response.getAuditRequestId()).isEqualTo(999L);
        assertThat(response.getStatus()).isEqualTo("PENDING");
        assertThat(response.getEntityType()).isEqualTo("CURRENCY_PAIR");
        assertThat(response.getActionType()).isEqualTo("CREATE");
        assertThat(response.getEntityId()).isNull();
        assertThat(response.getSummary()).isNotBlank();

        org.mockito.ArgumentCaptor<Object> afterCaptor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(auditService).submit(eq("CURRENCY_PAIR"), eq("CREATE"), isNull(), eq(1L), anyString(), isNull(),
                afterCaptor.capture(), eq("alice"));
        @SuppressWarnings("unchecked")
        Map<String, Object> after = (Map<String, Object>) afterCaptor.getValue();
        assertThat(after.get("rateType")).isEqualTo("AUTO");
        assertThat(after.get("rate")).isNull();
        verify(currencyPairMapper, never()).insert(any());
    }

    @Test
    void createWithValidManualRateSubmitsAuditRequest() {
        when(currencyPairDefinitionMapper.findById(10L)).thenReturn(sampleDefinition(10L, 4));
        when(brandMapper.findById(1L)).thenReturn(sampleBrand(1L, "au"));
        when(currencyPairMapper.findByDefinitionAndBrand(10L, 1L)).thenReturn(null);

        CurrencyPairCreateRequest request = new CurrencyPairCreateRequest(10L, 1L, "MANUAL",
                new BigDecimal("150.25"), false);
        AuditPendingResponse response = service.create(request, null);

        assertThat(response.getActionType()).isEqualTo("CREATE");

        org.mockito.ArgumentCaptor<Object> afterCaptor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(auditService).submit(eq("CURRENCY_PAIR"), eq("CREATE"), isNull(), eq(1L), anyString(), isNull(),
                afterCaptor.capture(), isNull());
        @SuppressWarnings("unchecked")
        Map<String, Object> after = (Map<String, Object>) afterCaptor.getValue();
        assertThat(after.get("rateType")).isEqualTo("MANUAL");
        assertThat((BigDecimal) after.get("rate")).isEqualByComparingTo(new BigDecimal("150.25"));
        verify(currencyPairMapper, never()).insert(any());
    }

    @Test
    void createDefaultsRateTypeToAutoAndActiveToFalse() {
        when(currencyPairDefinitionMapper.findById(10L)).thenReturn(sampleDefinition(10L, 4));
        when(brandMapper.findById(1L)).thenReturn(sampleBrand(1L, "au"));
        when(currencyPairMapper.findByDefinitionAndBrand(10L, 1L)).thenReturn(null);

        CurrencyPairCreateRequest request = new CurrencyPairCreateRequest(10L, 1L, null, null, null);
        service.create(request, null);

        org.mockito.ArgumentCaptor<Object> afterCaptor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(auditService).submit(eq("CURRENCY_PAIR"), eq("CREATE"), isNull(), eq(1L), anyString(), isNull(),
                afterCaptor.capture(), isNull());
        @SuppressWarnings("unchecked")
        Map<String, Object> after = (Map<String, Object>) afterCaptor.getValue();
        assertThat(after.get("rateType")).isEqualTo("AUTO");
        assertThat(after.get("active")).isEqualTo(false);
    }

    @Test
    void updateThrowsNotFoundWhenMissing() {
        when(currencyPairMapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.update(999L, new CurrencyPairUpdateRequest(null, null, true), null))
                .isInstanceOf(CurrencyPairNotFoundException.class);
        verify(auditService, never()).submit(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void updateTogglingActiveSubmitsAuditRequestWithEntityId() {
        when(currencyPairMapper.findById(1L))
                .thenReturn(sampleCurrencyPair(1L, 10L, 1L, "MANUAL", new BigDecimal("150.25"), false));
        when(currencyPairDefinitionMapper.findById(10L)).thenReturn(sampleDefinition(10L, 4));

        AuditPendingResponse response = service.update(1L, new CurrencyPairUpdateRequest(null, null, true), null);

        assertThat(response.getActionType()).isEqualTo("UPDATE");
        assertThat(response.getEntityId()).isEqualTo(1L);

        org.mockito.ArgumentCaptor<Object> beforeCaptor = org.mockito.ArgumentCaptor.forClass(Object.class);
        org.mockito.ArgumentCaptor<Object> afterCaptor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(auditService).submit(eq("CURRENCY_PAIR"), eq("UPDATE"), eq(1L), eq(1L), anyString(),
                beforeCaptor.capture(), afterCaptor.capture(), isNull());

        @SuppressWarnings("unchecked")
        Map<String, Object> before = (Map<String, Object>) beforeCaptor.getValue();
        @SuppressWarnings("unchecked")
        Map<String, Object> after = (Map<String, Object>) afterCaptor.getValue();
        assertThat(before.get("active")).isEqualTo(false);
        assertThat(after.get("active")).isEqualTo(true);
        assertThat(after.get("rateType")).isEqualTo("MANUAL");
        assertThat((BigDecimal) after.get("rate")).isEqualByComparingTo(new BigDecimal("150.25"));
        verify(currencyPairMapper, never()).update(any());
    }

    @Test
    void updateSwitchingFromManualToAutoClearsRateInAfterData() {
        when(currencyPairMapper.findById(1L))
                .thenReturn(sampleCurrencyPair(1L, 10L, 1L, "MANUAL", new BigDecimal("150.25"), false));
        when(currencyPairDefinitionMapper.findById(10L)).thenReturn(sampleDefinition(10L, 4));

        service.update(1L, new CurrencyPairUpdateRequest("AUTO", null, null), null);

        org.mockito.ArgumentCaptor<Object> afterCaptor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(auditService).submit(eq("CURRENCY_PAIR"), eq("UPDATE"), eq(1L), eq(1L), anyString(), any(),
                afterCaptor.capture(), isNull());
        @SuppressWarnings("unchecked")
        Map<String, Object> after = (Map<String, Object>) afterCaptor.getValue();
        assertThat(after.get("rateType")).isEqualTo("AUTO");
        assertThat(after.get("rate")).isNull();
    }

    @Test
    void updateRejectsManualRateExceedingPrecision() {
        when(currencyPairMapper.findById(1L))
                .thenReturn(sampleCurrencyPair(1L, 10L, 1L, "MANUAL", new BigDecimal("150.25"), false));
        when(currencyPairDefinitionMapper.findById(10L)).thenReturn(sampleDefinition(10L, 2));

        assertThatThrownBy(() -> service.update(1L,
                new CurrencyPairUpdateRequest(null, new BigDecimal("150.255"), null), null))
                .isInstanceOf(InvalidRequestException.class);
        verify(auditService, never()).submit(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void deleteThrowsNotFoundWhenMissing() {
        when(currencyPairMapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.delete(999L, null))
                .isInstanceOf(CurrencyPairNotFoundException.class);
        verify(auditService, never()).submit(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void deleteSubmitsAuditRequestWithNullAfterData() {
        when(currencyPairMapper.findById(1L))
                .thenReturn(sampleCurrencyPair(1L, 10L, 1L, "AUTO", null, true));

        AuditPendingResponse response = service.delete(1L, null);

        assertThat(response.getActionType()).isEqualTo("DELETE");

        org.mockito.ArgumentCaptor<Object> beforeCaptor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(auditService).submit(eq("CURRENCY_PAIR"), eq("DELETE"), eq(1L), eq(1L), anyString(),
                beforeCaptor.capture(), isNull(), isNull());
        @SuppressWarnings("unchecked")
        Map<String, Object> before = (Map<String, Object>) beforeCaptor.getValue();
        assertThat(before.get("active")).isEqualTo(true);
        verify(currencyPairMapper, never()).deleteById(anyLong());
    }
}
