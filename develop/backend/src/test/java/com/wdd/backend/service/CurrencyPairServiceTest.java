package com.wdd.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wdd.backend.dto.Brand;
import com.wdd.backend.dto.CurrencyPair;
import com.wdd.backend.dto.CurrencyPairCreateRequest;
import com.wdd.backend.dto.CurrencyPairDefinition;
import com.wdd.backend.dto.CurrencyPairResponse;
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
    private CurrencyPairService service;

    @BeforeEach
    void setUp() {
        currencyPairMapper = mock(CurrencyPairMapper.class);
        currencyPairDefinitionMapper = mock(CurrencyPairDefinitionMapper.class);
        brandMapper = mock(BrandMapper.class);
        service = new CurrencyPairService(currencyPairMapper, currencyPairDefinitionMapper, brandMapper);
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

        CurrencyPairResponse response = service.findById(1L);

        assertThat(response.getBaseCurrencyCode()).isEqualTo("USD");
        assertThat(response.getQuoteCurrencyCode()).isEqualTo("JPY");
        assertThat(response.getBrandCode()).isEqualTo("au");
    }

    @Test
    void createRejectsMissingDefinitionId() {
        CurrencyPairCreateRequest request = new CurrencyPairCreateRequest(null, 1L, "AUTO", null, false);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(InvalidRequestException.class);
        verify(currencyPairMapper, never()).insert(any());
    }

    @Test
    void createRejectsMissingBrandId() {
        CurrencyPairCreateRequest request = new CurrencyPairCreateRequest(10L, null, "AUTO", null, false);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void createRejectsNonExistentDefinition() {
        when(currencyPairDefinitionMapper.findById(10L)).thenReturn(null);
        CurrencyPairCreateRequest request = new CurrencyPairCreateRequest(10L, 1L, "AUTO", null, false);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void createRejectsNonExistentBrand() {
        when(currencyPairDefinitionMapper.findById(10L)).thenReturn(sampleDefinition(10L, 4));
        when(brandMapper.findById(1L)).thenReturn(null);
        CurrencyPairCreateRequest request = new CurrencyPairCreateRequest(10L, 1L, "AUTO", null, false);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void createRejectsDuplicateDefinitionAndBrand() {
        when(currencyPairDefinitionMapper.findById(10L)).thenReturn(sampleDefinition(10L, 4));
        when(brandMapper.findById(1L)).thenReturn(sampleBrand(1L, "au"));
        when(currencyPairMapper.findByDefinitionAndBrand(10L, 1L))
                .thenReturn(sampleCurrencyPair(5L, 10L, 1L, "AUTO", null, false));
        CurrencyPairCreateRequest request = new CurrencyPairCreateRequest(10L, 1L, "AUTO", null, false);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(CurrencyPairConflictException.class);
        verify(currencyPairMapper, never()).insert(any());
    }

    @Test
    void createWithAutoRateTypeIgnoresSentRateAndStoresNull() {
        when(currencyPairDefinitionMapper.findById(10L)).thenReturn(sampleDefinition(10L, 4));
        when(brandMapper.findById(1L)).thenReturn(sampleBrand(1L, "au"));
        when(currencyPairMapper.findByDefinitionAndBrand(10L, 1L)).thenReturn(null);
        doAnswer(invocation -> {
            CurrencyPair pair = invocation.getArgument(0);
            pair.setId(100L);
            return 1;
        }).when(currencyPairMapper).insert(any(CurrencyPair.class));
        when(currencyPairMapper.findById(100L))
                .thenReturn(sampleCurrencyPair(100L, 10L, 1L, "AUTO", null, false));

        CurrencyPairCreateRequest request = new CurrencyPairCreateRequest(10L, 1L, "AUTO",
                new BigDecimal("150.25"), false);
        CurrencyPairResponse response = service.create(request);

        org.mockito.ArgumentCaptor<CurrencyPair> captor = org.mockito.ArgumentCaptor.forClass(CurrencyPair.class);
        verify(currencyPairMapper).insert(captor.capture());
        assertThat(captor.getValue().getRateType()).isEqualTo("AUTO");
        assertThat(captor.getValue().getRate()).isNull();
        assertThat(response.getRate()).isNull();
    }

    @Test
    void createWithManualRateTypeAndNoRateThrows() {
        when(currencyPairDefinitionMapper.findById(10L)).thenReturn(sampleDefinition(10L, 4));
        when(brandMapper.findById(1L)).thenReturn(sampleBrand(1L, "au"));
        when(currencyPairMapper.findByDefinitionAndBrand(10L, 1L)).thenReturn(null);

        CurrencyPairCreateRequest request = new CurrencyPairCreateRequest(10L, 1L, "MANUAL", null, false);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(InvalidRequestException.class);
        verify(currencyPairMapper, never()).insert(any());
    }

    @Test
    void createWithManualRateTypeAndNonPositiveRateThrows() {
        when(currencyPairDefinitionMapper.findById(10L)).thenReturn(sampleDefinition(10L, 4));
        when(brandMapper.findById(1L)).thenReturn(sampleBrand(1L, "au"));
        when(currencyPairMapper.findByDefinitionAndBrand(10L, 1L)).thenReturn(null);

        CurrencyPairCreateRequest request = new CurrencyPairCreateRequest(10L, 1L, "MANUAL",
                new BigDecimal("0"), false);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void createWithManualRateExceedingPrecisionThrows() {
        when(currencyPairDefinitionMapper.findById(10L)).thenReturn(sampleDefinition(10L, 2));
        when(brandMapper.findById(1L)).thenReturn(sampleBrand(1L, "au"));
        when(currencyPairMapper.findByDefinitionAndBrand(10L, 1L)).thenReturn(null);

        CurrencyPairCreateRequest request = new CurrencyPairCreateRequest(10L, 1L, "MANUAL",
                new BigDecimal("150.255"), false);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void createWithValidManualRateSucceeds() {
        when(currencyPairDefinitionMapper.findById(10L)).thenReturn(sampleDefinition(10L, 4));
        when(brandMapper.findById(1L)).thenReturn(sampleBrand(1L, "au"));
        when(currencyPairMapper.findByDefinitionAndBrand(10L, 1L)).thenReturn(null);
        doAnswer(invocation -> {
            CurrencyPair pair = invocation.getArgument(0);
            pair.setId(101L);
            return 1;
        }).when(currencyPairMapper).insert(any(CurrencyPair.class));
        when(currencyPairMapper.findById(101L))
                .thenReturn(sampleCurrencyPair(101L, 10L, 1L, "MANUAL", new BigDecimal("150.25"), false));

        CurrencyPairCreateRequest request = new CurrencyPairCreateRequest(10L, 1L, "MANUAL",
                new BigDecimal("150.25"), false);
        CurrencyPairResponse response = service.create(request);

        assertThat(response.getRateType()).isEqualTo("MANUAL");
        assertThat(response.getRate()).isEqualByComparingTo("150.25");
    }

    @Test
    void createDefaultsRateTypeToAutoAndActiveToFalse() {
        when(currencyPairDefinitionMapper.findById(10L)).thenReturn(sampleDefinition(10L, 4));
        when(brandMapper.findById(1L)).thenReturn(sampleBrand(1L, "au"));
        when(currencyPairMapper.findByDefinitionAndBrand(10L, 1L)).thenReturn(null);
        doAnswer(invocation -> {
            CurrencyPair pair = invocation.getArgument(0);
            pair.setId(102L);
            return 1;
        }).when(currencyPairMapper).insert(any(CurrencyPair.class));
        when(currencyPairMapper.findById(102L))
                .thenReturn(sampleCurrencyPair(102L, 10L, 1L, "AUTO", null, false));

        CurrencyPairCreateRequest request = new CurrencyPairCreateRequest(10L, 1L, null, null, null);
        service.create(request);

        org.mockito.ArgumentCaptor<CurrencyPair> captor = org.mockito.ArgumentCaptor.forClass(CurrencyPair.class);
        verify(currencyPairMapper).insert(captor.capture());
        assertThat(captor.getValue().getRateType()).isEqualTo("AUTO");
        assertThat(captor.getValue().getActive()).isFalse();
    }

    @Test
    void updateThrowsNotFoundWhenMissing() {
        when(currencyPairMapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.update(999L, new CurrencyPairUpdateRequest(null, null, true)))
                .isInstanceOf(CurrencyPairNotFoundException.class);
    }

    @Test
    void updateTogglesActiveIndependentlyOfRateTypeAndRate() {
        when(currencyPairMapper.findById(1L))
                .thenReturn(sampleCurrencyPair(1L, 10L, 1L, "MANUAL", new BigDecimal("150.25"), false));
        when(currencyPairDefinitionMapper.findById(10L)).thenReturn(sampleDefinition(10L, 4));
        when(currencyPairMapper.findById(1L))
                .thenReturn(sampleCurrencyPair(1L, 10L, 1L, "MANUAL", new BigDecimal("150.25"), false))
                .thenReturn(sampleCurrencyPair(1L, 10L, 1L, "MANUAL", new BigDecimal("150.25"), true));

        CurrencyPairResponse response = service.update(1L, new CurrencyPairUpdateRequest(null, null, true));

        org.mockito.ArgumentCaptor<CurrencyPair> captor = org.mockito.ArgumentCaptor.forClass(CurrencyPair.class);
        verify(currencyPairMapper).update(captor.capture());
        assertThat(captor.getValue().getRateType()).isEqualTo("MANUAL");
        assertThat(captor.getValue().getRate()).isEqualByComparingTo("150.25");
        assertThat(captor.getValue().getActive()).isTrue();
        assertThat(response.getActive()).isTrue();
    }

    @Test
    void updateSwitchingFromManualToAutoClearsRate() {
        when(currencyPairMapper.findById(1L))
                .thenReturn(sampleCurrencyPair(1L, 10L, 1L, "MANUAL", new BigDecimal("150.25"), false))
                .thenReturn(sampleCurrencyPair(1L, 10L, 1L, "AUTO", null, false));
        when(currencyPairDefinitionMapper.findById(10L)).thenReturn(sampleDefinition(10L, 4));

        service.update(1L, new CurrencyPairUpdateRequest("AUTO", null, null));

        org.mockito.ArgumentCaptor<CurrencyPair> captor = org.mockito.ArgumentCaptor.forClass(CurrencyPair.class);
        verify(currencyPairMapper).update(captor.capture());
        assertThat(captor.getValue().getRateType()).isEqualTo("AUTO");
        assertThat(captor.getValue().getRate()).isNull();
    }

    @Test
    void updateRejectsManualRateExceedingPrecision() {
        when(currencyPairMapper.findById(1L))
                .thenReturn(sampleCurrencyPair(1L, 10L, 1L, "MANUAL", new BigDecimal("150.25"), false));
        when(currencyPairDefinitionMapper.findById(10L)).thenReturn(sampleDefinition(10L, 2));

        assertThatThrownBy(() -> service.update(1L,
                new CurrencyPairUpdateRequest(null, new BigDecimal("150.255"), null)))
                .isInstanceOf(InvalidRequestException.class);
        verify(currencyPairMapper, never()).update(any());
    }

    @Test
    void deleteThrowsNotFoundWhenMissing() {
        when(currencyPairMapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.delete(999L))
                .isInstanceOf(CurrencyPairNotFoundException.class);
        verify(currencyPairMapper, never()).deleteById(anyLong());
    }

    @Test
    void deleteRemovesRowRegardlessOfActiveState() {
        when(currencyPairMapper.findById(1L))
                .thenReturn(sampleCurrencyPair(1L, 10L, 1L, "AUTO", null, true));

        service.delete(1L);

        verify(currencyPairMapper).deleteById(1L);
    }
}
