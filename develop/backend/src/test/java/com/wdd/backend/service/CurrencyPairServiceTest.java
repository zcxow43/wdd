package com.wdd.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.wdd.backend.dto.CurrencyPairCreateRequest;
import com.wdd.backend.dto.CurrencyPairResponse;
import com.wdd.backend.dto.CurrencyPairUpdateRequest;
import com.wdd.backend.exception.BrandNotFoundException;
import com.wdd.backend.exception.CurrencyNotFoundException;
import com.wdd.backend.exception.CurrencyPairExistsException;
import com.wdd.backend.exception.CurrencyPairNotFoundException;
import com.wdd.backend.exception.InvalidCurrencyPairException;
import com.wdd.backend.mapper.BrandMapper;
import com.wdd.backend.mapper.CurrencyMapper;
import com.wdd.backend.mapper.CurrencyPairMapper;
import com.wdd.backend.model.Brand;
import com.wdd.backend.model.Currency;
import com.wdd.backend.model.CurrencyPair;

@ExtendWith(MockitoExtension.class)
class CurrencyPairServiceTest {

    @Mock
    private CurrencyPairMapper currencyPairMapper;

    @Mock
    private BrandMapper brandMapper;

    @Mock
    private CurrencyMapper currencyMapper;

    private CurrencyPairService currencyPairService;

    @BeforeEach
    void setUp() {
        CurrencyPairValidator validator = new CurrencyPairValidator(brandMapper, currencyMapper, currencyPairMapper);
        currencyPairService = new CurrencyPairService(currencyPairMapper, validator);
    }

    private CurrencyPair samplePair() {
        CurrencyPair pair = new CurrencyPair();
        pair.setId(1L);
        pair.setBrandId(3L);
        pair.setBaseCurrencyId(2L);
        pair.setQuoteCurrencyId(1L);
        pair.setRate(new BigDecimal("32.5"));
        pair.setRateType("MANUAL");
        pair.setActive(true);
        pair.setCreatedAt(LocalDateTime.now());
        pair.setUpdatedAt(LocalDateTime.now());
        pair.setBrandCode("PUG");
        pair.setBaseCurrencyCode("USD");
        pair.setQuoteCurrencyCode("TWD");
        return pair;
    }

    private Brand sampleBrand(Long id) {
        Brand brand = new Brand();
        brand.setId(id);
        brand.setCode("PUG");
        brand.setName("PUG");
        brand.setActive(true);
        return brand;
    }

    private Currency sampleCurrency(Long id, String code) {
        Currency currency = new Currency();
        currency.setId(id);
        currency.setCode(code);
        currency.setName(code);
        currency.setDecimalPlaces(2);
        return currency;
    }

    private CurrencyPairCreateRequest manualCreateRequest() {
        CurrencyPairCreateRequest request = new CurrencyPairCreateRequest();
        request.setBrandId(3L);
        request.setBaseCurrencyId(2L);
        request.setQuoteCurrencyId(1L);
        request.setRate(new BigDecimal("32.5"));
        request.setRateType("MANUAL");
        return request;
    }

    private void stubBrandAndCurrenciesExist() {
        when(brandMapper.findById(3L)).thenReturn(Optional.of(sampleBrand(3L)));
        when(currencyMapper.findById(2L)).thenReturn(Optional.of(sampleCurrency(2L, "USD")));
        when(currencyMapper.findById(1L)).thenReturn(Optional.of(sampleCurrency(1L, "TWD")));
    }

    @Test
    void list_returnsAllPairs() {
        when(currencyPairMapper.findAll(isNull(), isNull())).thenReturn(List.of(samplePair()));

        List<CurrencyPairResponse> result = currencyPairService.list(null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getBrandCode()).isEqualTo("PUG");
        assertThat(result.get(0).getBaseCurrencyCode()).isEqualTo("USD");
        assertThat(result.get(0).getQuoteCurrencyCode()).isEqualTo("TWD");
    }

    @Test
    void list_filtersByBrandIdAndActive() {
        when(currencyPairMapper.findAll(eq(3L), eq(true))).thenReturn(List.of(samplePair()));

        List<CurrencyPairResponse> result = currencyPairService.list(3L, true);

        assertThat(result).hasSize(1);
        verify(currencyPairMapper).findAll(eq(3L), eq(true));
    }

    @Test
    void getById_returnsPairWhenFound() {
        when(currencyPairMapper.findById(1L)).thenReturn(Optional.of(samplePair()));

        CurrencyPairResponse result = currencyPairService.getById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getRate()).isEqualByComparingTo("32.5");
    }

    @Test
    void getById_throwsNotFoundWhenMissing() {
        when(currencyPairMapper.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> currencyPairService.getById(999L))
                .isInstanceOf(CurrencyPairNotFoundException.class);
    }

    @Test
    void create_createsAndReturnsPair() {
        stubBrandAndCurrenciesExist();
        when(currencyPairMapper.findByBrandBaseQuote(3L, 2L, 1L, null)).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            CurrencyPair p = invocation.getArgument(0);
            p.setId(10L);
            return 1;
        }).when(currencyPairMapper).insert(any(CurrencyPair.class));
        when(currencyPairMapper.findById(10L)).thenReturn(Optional.of(samplePair()));

        CurrencyPairResponse result = currencyPairService.create(manualCreateRequest());

        assertThat(result.getId()).isEqualTo(1L);
        verify(currencyPairMapper).insert(any(CurrencyPair.class));
    }

    @Test
    void create_throwsNotFoundWhenBrandMissing() {
        when(brandMapper.findById(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> currencyPairService.create(manualCreateRequest()))
                .isInstanceOf(BrandNotFoundException.class);

        verify(currencyPairMapper, never()).insert(any(CurrencyPair.class));
    }

    @Test
    void create_throwsNotFoundWhenBaseCurrencyMissing() {
        when(brandMapper.findById(3L)).thenReturn(Optional.of(sampleBrand(3L)));
        when(currencyMapper.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> currencyPairService.create(manualCreateRequest()))
                .isInstanceOf(CurrencyNotFoundException.class);

        verify(currencyPairMapper, never()).insert(any(CurrencyPair.class));
    }

    @Test
    void create_throwsNotFoundWhenQuoteCurrencyMissing() {
        when(brandMapper.findById(3L)).thenReturn(Optional.of(sampleBrand(3L)));
        when(currencyMapper.findById(2L)).thenReturn(Optional.of(sampleCurrency(2L, "USD")));
        when(currencyMapper.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> currencyPairService.create(manualCreateRequest()))
                .isInstanceOf(CurrencyNotFoundException.class);

        verify(currencyPairMapper, never()).insert(any(CurrencyPair.class));
    }

    @Test
    void create_throwsBadRequestWhenBaseEqualsQuote() {
        when(brandMapper.findById(3L)).thenReturn(Optional.of(sampleBrand(3L)));
        when(currencyMapper.findById(2L)).thenReturn(Optional.of(sampleCurrency(2L, "USD")));

        CurrencyPairCreateRequest request = manualCreateRequest();
        request.setQuoteCurrencyId(2L);

        assertThatThrownBy(() -> currencyPairService.create(request))
                .isInstanceOf(InvalidCurrencyPairException.class);

        verify(currencyPairMapper, never()).insert(any(CurrencyPair.class));
    }

    @Test
    void create_throwsConflictWhenDuplicatePair() {
        stubBrandAndCurrenciesExist();
        when(currencyPairMapper.findByBrandBaseQuote(3L, 2L, 1L, null)).thenReturn(Optional.of(samplePair()));

        assertThatThrownBy(() -> currencyPairService.create(manualCreateRequest()))
                .isInstanceOf(CurrencyPairExistsException.class);

        verify(currencyPairMapper, never()).insert(any(CurrencyPair.class));
    }

    @Test
    void create_sameBaseQuoteUnderDifferentBrandSucceeds() {
        stubBrandAndCurrenciesExist();
        // Uniqueness check is scoped to (brandId, base, quote) — a different brand never
        // collides, so the mapper correctly returns empty for this brand's scope.
        when(currencyPairMapper.findByBrandBaseQuote(3L, 2L, 1L, null)).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            CurrencyPair p = invocation.getArgument(0);
            p.setId(11L);
            return 1;
        }).when(currencyPairMapper).insert(any(CurrencyPair.class));
        when(currencyPairMapper.findById(11L)).thenReturn(Optional.of(samplePair()));

        CurrencyPairResponse result = currencyPairService.create(manualCreateRequest());

        assertThat(result).isNotNull();
        verify(currencyPairMapper).insert(any(CurrencyPair.class));
    }

    @Test
    void create_throwsBadRequestWhenManualWithoutRate() {
        stubBrandAndCurrenciesExist();
        when(currencyPairMapper.findByBrandBaseQuote(3L, 2L, 1L, null)).thenReturn(Optional.empty());

        CurrencyPairCreateRequest request = manualCreateRequest();
        request.setRate(null);

        assertThatThrownBy(() -> currencyPairService.create(request))
                .isInstanceOf(InvalidCurrencyPairException.class)
                .hasMessageContaining("rateType is MANUAL");

        verify(currencyPairMapper, never()).insert(any(CurrencyPair.class));
    }

    @Test
    void create_throwsBadRequestWhenManualWithZeroRate() {
        stubBrandAndCurrenciesExist();
        when(currencyPairMapper.findByBrandBaseQuote(3L, 2L, 1L, null)).thenReturn(Optional.empty());

        CurrencyPairCreateRequest request = manualCreateRequest();
        request.setRate(BigDecimal.ZERO);

        assertThatThrownBy(() -> currencyPairService.create(request))
                .isInstanceOf(InvalidCurrencyPairException.class);

        verify(currencyPairMapper, never()).insert(any(CurrencyPair.class));
    }

    @Test
    void create_throwsBadRequestWhenManualWithNegativeRate() {
        stubBrandAndCurrenciesExist();
        when(currencyPairMapper.findByBrandBaseQuote(3L, 2L, 1L, null)).thenReturn(Optional.empty());

        CurrencyPairCreateRequest request = manualCreateRequest();
        request.setRate(new BigDecimal("-5"));

        assertThatThrownBy(() -> currencyPairService.create(request))
                .isInstanceOf(InvalidCurrencyPairException.class);

        verify(currencyPairMapper, never()).insert(any(CurrencyPair.class));
    }

    @Test
    void create_autoWithRateSupplied_clearsToNull() {
        stubBrandAndCurrenciesExist();
        when(currencyPairMapper.findByBrandBaseQuote(3L, 2L, 1L, null)).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            CurrencyPair p = invocation.getArgument(0);
            assertThat(p.getRate()).isNull();
            p.setId(12L);
            return 1;
        }).when(currencyPairMapper).insert(any(CurrencyPair.class));
        CurrencyPair autoPair = samplePair();
        autoPair.setRateType("AUTO");
        autoPair.setRate(null);
        when(currencyPairMapper.findById(12L)).thenReturn(Optional.of(autoPair));

        CurrencyPairCreateRequest request = manualCreateRequest();
        request.setRateType("AUTO");
        request.setRate(new BigDecimal("99.99"));

        CurrencyPairResponse result = currencyPairService.create(request);

        assertThat(result.getRate()).isNull();
    }

    @Test
    void create_autoWithoutRate_succeedsWithNullRate() {
        stubBrandAndCurrenciesExist();
        when(currencyPairMapper.findByBrandBaseQuote(3L, 2L, 1L, null)).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            CurrencyPair p = invocation.getArgument(0);
            assertThat(p.getRate()).isNull();
            p.setId(13L);
            return 1;
        }).when(currencyPairMapper).insert(any(CurrencyPair.class));
        CurrencyPair autoPair = samplePair();
        autoPair.setRateType("AUTO");
        autoPair.setRate(null);
        when(currencyPairMapper.findById(13L)).thenReturn(Optional.of(autoPair));

        CurrencyPairCreateRequest request = manualCreateRequest();
        request.setRateType("AUTO");
        request.setRate(null);

        CurrencyPairResponse result = currencyPairService.create(request);

        assertThat(result.getRate()).isNull();
    }

    @Test
    void update_updatesAndReturnsPair() {
        CurrencyPair existing = samplePair();
        when(currencyPairMapper.findById(1L)).thenReturn(Optional.of(existing));
        when(currencyPairMapper.findByBrandBaseQuote(eq(3L), eq(2L), eq(1L), eq(1L))).thenReturn(Optional.empty());

        CurrencyPairUpdateRequest request = new CurrencyPairUpdateRequest();
        request.setActive(false);

        CurrencyPair updated = samplePair();
        updated.setActive(false);
        when(currencyPairMapper.findById(1L)).thenReturn(Optional.of(existing), Optional.of(updated));

        CurrencyPairResponse result = currencyPairService.update(1L, request);

        assertThat(result.getActive()).isFalse();
        verify(currencyPairMapper).update(any(CurrencyPair.class));
    }

    @Test
    void update_throwsNotFoundWhenMissing() {
        when(currencyPairMapper.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> currencyPairService.update(999L, new CurrencyPairUpdateRequest()))
                .isInstanceOf(CurrencyPairNotFoundException.class);
    }

    @Test
    void update_throwsNotFoundWhenNewBrandMissing() {
        when(currencyPairMapper.findById(1L)).thenReturn(Optional.of(samplePair()));
        when(brandMapper.findById(99L)).thenReturn(Optional.empty());

        CurrencyPairUpdateRequest request = new CurrencyPairUpdateRequest();
        request.setBrandId(99L);

        assertThatThrownBy(() -> currencyPairService.update(1L, request))
                .isInstanceOf(BrandNotFoundException.class);

        verify(currencyPairMapper, never()).update(any(CurrencyPair.class));
    }

    @Test
    void update_throwsConflictWhenCollidesWithAnotherRow() {
        when(currencyPairMapper.findById(1L)).thenReturn(Optional.of(samplePair()));
        when(currencyPairMapper.findByBrandBaseQuote(eq(3L), eq(2L), eq(1L), eq(1L)))
                .thenReturn(Optional.of(samplePair()));

        assertThatThrownBy(() -> currencyPairService.update(1L, new CurrencyPairUpdateRequest()))
                .isInstanceOf(CurrencyPairExistsException.class);

        verify(currencyPairMapper, never()).update(any(CurrencyPair.class));
    }

    @Test
    void update_manualToAuto_clearsRateEvenIfSupplied() {
        CurrencyPair existing = samplePair();
        when(currencyPairMapper.findById(1L)).thenReturn(Optional.of(existing));
        when(currencyPairMapper.findByBrandBaseQuote(eq(3L), eq(2L), eq(1L), eq(1L))).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            CurrencyPair p = invocation.getArgument(0);
            assertThat(p.getRate()).isNull();
            return 1;
        }).when(currencyPairMapper).update(any(CurrencyPair.class));

        CurrencyPairUpdateRequest request = new CurrencyPairUpdateRequest();
        request.setRateType("AUTO");
        request.setRate(new BigDecimal("50.0"));

        currencyPairService.update(1L, request);

        verify(currencyPairMapper).update(any(CurrencyPair.class));
    }

    @Test
    void update_autoToManualWithoutRate_throws400() {
        CurrencyPair existing = samplePair();
        existing.setRateType("AUTO");
        existing.setRate(null);
        when(currencyPairMapper.findById(1L)).thenReturn(Optional.of(existing));
        when(currencyPairMapper.findByBrandBaseQuote(eq(3L), eq(2L), eq(1L), eq(1L))).thenReturn(Optional.empty());

        CurrencyPairUpdateRequest request = new CurrencyPairUpdateRequest();
        request.setRateType("MANUAL");

        assertThatThrownBy(() -> currencyPairService.update(1L, request))
                .isInstanceOf(InvalidCurrencyPairException.class);

        verify(currencyPairMapper, never()).update(any(CurrencyPair.class));
    }

    @Test
    void update_autoToManualWithRate_succeeds() {
        CurrencyPair existing = samplePair();
        existing.setRateType("AUTO");
        existing.setRate(null);
        when(currencyPairMapper.findById(1L)).thenReturn(Optional.of(existing));
        when(currencyPairMapper.findByBrandBaseQuote(eq(3L), eq(2L), eq(1L), eq(1L))).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            CurrencyPair p = invocation.getArgument(0);
            assertThat(p.getRate()).isEqualByComparingTo("40.0");
            return 1;
        }).when(currencyPairMapper).update(any(CurrencyPair.class));

        CurrencyPairUpdateRequest request = new CurrencyPairUpdateRequest();
        request.setRateType("MANUAL");
        request.setRate(new BigDecimal("40.0"));

        currencyPairService.update(1L, request);

        verify(currencyPairMapper).update(any(CurrencyPair.class));
    }

    @Test
    void update_manualOmittingRate_keepsExistingRate() {
        CurrencyPair existing = samplePair();
        existing.setRateType("MANUAL");
        existing.setRate(new BigDecimal("32.5"));
        when(currencyPairMapper.findById(1L)).thenReturn(Optional.of(existing));
        when(currencyPairMapper.findByBrandBaseQuote(eq(3L), eq(2L), eq(1L), eq(1L))).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            CurrencyPair p = invocation.getArgument(0);
            assertThat(p.getRate()).isEqualByComparingTo("32.5");
            return 1;
        }).when(currencyPairMapper).update(any(CurrencyPair.class));

        CurrencyPairUpdateRequest request = new CurrencyPairUpdateRequest();
        request.setActive(false);

        currencyPairService.update(1L, request);

        verify(currencyPairMapper).update(any(CurrencyPair.class));
    }

    @Test
    void delete_deletesWhenFound() {
        when(currencyPairMapper.findById(1L)).thenReturn(Optional.of(samplePair()));

        currencyPairService.delete(1L);

        verify(currencyPairMapper).deleteById(1L);
    }

    @Test
    void delete_throwsNotFoundWhenMissing() {
        when(currencyPairMapper.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> currencyPairService.delete(999L))
                .isInstanceOf(CurrencyPairNotFoundException.class);

        verify(currencyPairMapper, never()).deleteById(anyLong());
    }
}
