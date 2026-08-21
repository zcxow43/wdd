package com.wdd.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wdd.backend.dto.Brand;
import com.wdd.backend.dto.Currency;
import com.wdd.backend.dto.CurrencyPair;
import com.wdd.backend.dto.CurrencyPairDefinition;
import com.wdd.backend.dto.CurrencyPairDefinitionCreateRequest;
import com.wdd.backend.dto.CurrencyPairDefinitionCreateResponse;
import com.wdd.backend.dto.CurrencyPairDefinitionResponse;
import com.wdd.backend.dto.CurrencyPairDefinitionUpdateRequest;
import com.wdd.backend.exception.ActiveCurrencyPairsExistException;
import com.wdd.backend.exception.CurrencyPairDefinitionConflictException;
import com.wdd.backend.exception.CurrencyPairDefinitionNotFoundException;
import com.wdd.backend.exception.InvalidRequestException;
import com.wdd.backend.mapper.BrandMapper;
import com.wdd.backend.mapper.CurrencyMapper;
import com.wdd.backend.mapper.CurrencyPairDefinitionMapper;
import com.wdd.backend.mapper.CurrencyPairMapper;

class CurrencyPairDefinitionServiceTest {

    private CurrencyPairDefinitionMapper currencyPairDefinitionMapper;
    private CurrencyPairMapper currencyPairMapper;
    private CurrencyMapper currencyMapper;
    private BrandMapper brandMapper;
    private CurrencyPairDefinitionService service;

    @BeforeEach
    void setUp() {
        currencyPairDefinitionMapper = mock(CurrencyPairDefinitionMapper.class);
        currencyPairMapper = mock(CurrencyPairMapper.class);
        currencyMapper = mock(CurrencyMapper.class);
        brandMapper = mock(BrandMapper.class);
        service = new CurrencyPairDefinitionService(currencyPairDefinitionMapper, currencyPairMapper,
                currencyMapper, brandMapper);
    }

    private static Currency sampleCurrency(Long id, String code) {
        Currency currency = new Currency();
        currency.setId(id);
        currency.setCode(code);
        currency.setName(code);
        currency.setSymbol(code);
        currency.setDecimalPlaces(2);
        return currency;
    }

    private static Brand sampleBrand(Long id, String code) {
        Brand brand = new Brand();
        brand.setId(id);
        brand.setCode(code);
        brand.setName(code);
        brand.setActive(true);
        return brand;
    }

    private static CurrencyPairDefinition sampleDefinition(Long id, Long baseId, String baseCode,
            Long quoteId, String quoteCode, Integer precision) {
        CurrencyPairDefinition definition = new CurrencyPairDefinition();
        definition.setId(id);
        definition.setBaseCurrencyId(baseId);
        definition.setBaseCurrencyCode(baseCode);
        definition.setQuoteCurrencyId(quoteId);
        definition.setQuoteCurrencyCode(quoteCode);
        definition.setPrecision(precision);
        definition.setCreatedAt(LocalDateTime.now());
        definition.setUpdatedAt(LocalDateTime.now());
        return definition;
    }

    @Test
    void findByIdThrowsWhenMissing() {
        when(currencyPairDefinitionMapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.findById(999L))
                .isInstanceOf(CurrencyPairDefinitionNotFoundException.class);
    }

    @Test
    void findByIdReturnsMappedResponse() {
        when(currencyPairDefinitionMapper.findById(1L))
                .thenReturn(sampleDefinition(1L, 1L, "USD", 2L, "JPY", 4));

        CurrencyPairDefinitionResponse response = service.findById(1L);

        assertThat(response.getBaseCurrencyCode()).isEqualTo("USD");
        assertThat(response.getQuoteCurrencyCode()).isEqualTo("JPY");
        assertThat(response.getPrecision()).isEqualTo(4);
    }

    @Test
    void createRejectsMissingBaseCurrencyId() {
        CurrencyPairDefinitionCreateRequest request = new CurrencyPairDefinitionCreateRequest(null, 2L, 4);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(InvalidRequestException.class);
        verify(currencyPairDefinitionMapper, never()).insert(any());
    }

    @Test
    void createRejectsMissingQuoteCurrencyId() {
        CurrencyPairDefinitionCreateRequest request = new CurrencyPairDefinitionCreateRequest(1L, null, 4);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void createRejectsSameBaseAndQuoteCurrency() {
        CurrencyPairDefinitionCreateRequest request = new CurrencyPairDefinitionCreateRequest(1L, 1L, 4);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void createRejectsNonExistentBaseCurrency() {
        when(currencyMapper.findById(1L)).thenReturn(null);
        CurrencyPairDefinitionCreateRequest request = new CurrencyPairDefinitionCreateRequest(1L, 2L, 4);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void createRejectsNonExistentQuoteCurrency() {
        when(currencyMapper.findById(1L)).thenReturn(sampleCurrency(1L, "USD"));
        when(currencyMapper.findById(2L)).thenReturn(null);
        CurrencyPairDefinitionCreateRequest request = new CurrencyPairDefinitionCreateRequest(1L, 2L, 4);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void createRejectsOutOfRangePrecision() {
        when(currencyMapper.findById(1L)).thenReturn(sampleCurrency(1L, "USD"));
        when(currencyMapper.findById(2L)).thenReturn(sampleCurrency(2L, "JPY"));
        CurrencyPairDefinitionCreateRequest request = new CurrencyPairDefinitionCreateRequest(1L, 2L, 9);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void createRejectsDuplicatePair() {
        when(currencyMapper.findById(1L)).thenReturn(sampleCurrency(1L, "USD"));
        when(currencyMapper.findById(2L)).thenReturn(sampleCurrency(2L, "JPY"));
        when(currencyPairDefinitionMapper.findByCurrencyPair(1L, 2L))
                .thenReturn(sampleDefinition(1L, 1L, "USD", 2L, "JPY", 4));
        CurrencyPairDefinitionCreateRequest request = new CurrencyPairDefinitionCreateRequest(1L, 2L, 4);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(CurrencyPairDefinitionConflictException.class);
        verify(currencyPairDefinitionMapper, never()).insert(any());
    }

    @Test
    void createInsertsDefinitionAndFansOutOnePairPerBrand() {
        when(currencyMapper.findById(1L)).thenReturn(sampleCurrency(1L, "USD"));
        when(currencyMapper.findById(2L)).thenReturn(sampleCurrency(2L, "JPY"));
        when(currencyPairDefinitionMapper.findByCurrencyPair(1L, 2L)).thenReturn(null);
        doAnswer(invocation -> {
            CurrencyPairDefinition definition = invocation.getArgument(0);
            definition.setId(10L);
            return 1;
        }).when(currencyPairDefinitionMapper).insert(any(CurrencyPairDefinition.class));
        when(currencyPairDefinitionMapper.findById(10L))
                .thenReturn(sampleDefinition(10L, 1L, "USD", 2L, "JPY", 4));
        when(brandMapper.findAll(isNull())).thenReturn(List.of(
                sampleBrand(1L, "au"), sampleBrand(2L, "vt")));

        CurrencyPair pairAu = new CurrencyPair();
        pairAu.setId(100L);
        pairAu.setCurrencyPairDefinitionId(10L);
        pairAu.setBrandId(1L);
        pairAu.setBrandCode("au");
        pairAu.setRateType("AUTO");
        pairAu.setActive(false);

        CurrencyPair pairVt = new CurrencyPair();
        pairVt.setId(101L);
        pairVt.setCurrencyPairDefinitionId(10L);
        pairVt.setBrandId(2L);
        pairVt.setBrandCode("vt");
        pairVt.setRateType("AUTO");
        pairVt.setActive(false);

        when(currencyPairMapper.findByDefinitionId(10L)).thenReturn(List.of(pairAu, pairVt));

        CurrencyPairDefinitionCreateRequest request = new CurrencyPairDefinitionCreateRequest(1L, 2L, null);
        CurrencyPairDefinitionCreateResponse response = service.create(request);

        verify(currencyPairDefinitionMapper, times(1)).insert(any(CurrencyPairDefinition.class));
        verify(currencyPairMapper, times(2)).insert(any(CurrencyPair.class));
        assertThat(response.getPrecision()).isEqualTo(4);
        assertThat(response.getCurrencyPairs()).hasSize(2);
        assertThat(response.getCurrencyPairs()).extracting("brandCode").containsExactlyInAnyOrder("au", "vt");
        assertThat(response.getCurrencyPairs()).allSatisfy(p -> {
            assertThat(p.getRateType()).isEqualTo("AUTO");
            assertThat(p.getRate()).isNull();
            assertThat(p.getActive()).isFalse();
        });
    }

    @Test
    void updateThrowsNotFoundWhenMissing() {
        when(currencyPairDefinitionMapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.update(999L, new CurrencyPairDefinitionUpdateRequest(4)))
                .isInstanceOf(CurrencyPairDefinitionNotFoundException.class);
    }

    @Test
    void updateRejectsOutOfRangePrecision() {
        when(currencyPairDefinitionMapper.findById(1L))
                .thenReturn(sampleDefinition(1L, 1L, "USD", 2L, "JPY", 4));

        assertThatThrownBy(() -> service.update(1L, new CurrencyPairDefinitionUpdateRequest(9)))
                .isInstanceOf(InvalidRequestException.class);
        verify(currencyPairDefinitionMapper, never()).updatePrecision(anyLong(), any());
    }

    @Test
    void updateRejectsMissingPrecision() {
        when(currencyPairDefinitionMapper.findById(1L))
                .thenReturn(sampleDefinition(1L, 1L, "USD", 2L, "JPY", 4));

        assertThatThrownBy(() -> service.update(1L, new CurrencyPairDefinitionUpdateRequest(null)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void updateAppliesPrecisionAndIgnoresCurrencyIds() {
        when(currencyPairDefinitionMapper.findById(1L))
                .thenReturn(sampleDefinition(1L, 1L, "USD", 2L, "JPY", 4))
                .thenReturn(sampleDefinition(1L, 1L, "USD", 2L, "JPY", 6));

        CurrencyPairDefinitionResponse response = service.update(1L, new CurrencyPairDefinitionUpdateRequest(6));

        verify(currencyPairDefinitionMapper, times(1)).updatePrecision(eq(1L), eq(6));
        assertThat(response.getPrecision()).isEqualTo(6);
        assertThat(response.getBaseCurrencyId()).isEqualTo(1L);
        assertThat(response.getQuoteCurrencyId()).isEqualTo(2L);
    }

    @Test
    void deleteThrowsNotFoundWhenMissing() {
        when(currencyPairDefinitionMapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.delete(999L))
                .isInstanceOf(CurrencyPairDefinitionNotFoundException.class);
        verify(currencyPairDefinitionMapper, never()).deleteById(anyLong());
    }

    @Test
    void deleteThrowsWhenActiveBrandPairsExist() {
        when(currencyPairDefinitionMapper.findById(1L))
                .thenReturn(sampleDefinition(1L, 1L, "USD", 2L, "JPY", 4));
        when(currencyPairMapper.findActiveBrandCodesByDefinitionId(1L)).thenReturn(List.of("au", "vt"));

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(ActiveCurrencyPairsExistException.class)
                .satisfies(ex -> assertThat(((ActiveCurrencyPairsExistException) ex).getActiveBrandCodes())
                        .containsExactly("au", "vt"));
        verify(currencyPairDefinitionMapper, never()).deleteById(anyLong());
    }

    @Test
    void deleteRemovesDefinitionWhenNoActivePairs() {
        when(currencyPairDefinitionMapper.findById(1L))
                .thenReturn(sampleDefinition(1L, 1L, "USD", 2L, "JPY", 4));
        when(currencyPairMapper.findActiveBrandCodesByDefinitionId(1L)).thenReturn(List.of());

        service.delete(1L);

        verify(currencyPairDefinitionMapper, times(1)).deleteById(1L);
    }
}
