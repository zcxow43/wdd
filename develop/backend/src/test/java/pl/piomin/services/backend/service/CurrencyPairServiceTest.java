package pl.piomin.services.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import pl.piomin.services.backend.dto.CurrencyPairCreateRequest;
import pl.piomin.services.backend.dto.CurrencyPairUpdateRequest;
import pl.piomin.services.backend.exception.BrandNotFoundException;
import pl.piomin.services.backend.exception.CurrencyNotFoundException;
import pl.piomin.services.backend.exception.CurrencyPairExistsException;
import pl.piomin.services.backend.exception.CurrencyPairNotFoundException;
import pl.piomin.services.backend.exception.InvalidCurrencyPairException;
import pl.piomin.services.backend.mapper.BrandMapper;
import pl.piomin.services.backend.mapper.CurrencyMapper;
import pl.piomin.services.backend.mapper.CurrencyPairMapper;
import pl.piomin.services.backend.model.Brand;
import pl.piomin.services.backend.model.Currency;
import pl.piomin.services.backend.model.CurrencyPair;

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
        currency.setActive(true);
        return currency;
    }

    private CurrencyPair samplePair(Long id, Long brandId, Long baseId, Long quoteId) {
        CurrencyPair pair = new CurrencyPair();
        pair.setId(id);
        pair.setBrandId(brandId);
        pair.setBaseCurrencyId(baseId);
        pair.setQuoteCurrencyId(quoteId);
        pair.setRate(new BigDecimal("32.5"));
        pair.setRateType("MANUAL");
        pair.setActive(true);
        return pair;
    }

    private CurrencyPairCreateRequest sampleCreateRequest(Long brandId, Long baseId, Long quoteId) {
        CurrencyPairCreateRequest request = new CurrencyPairCreateRequest();
        request.setBrandId(brandId);
        request.setBaseCurrencyId(baseId);
        request.setQuoteCurrencyId(quoteId);
        request.setRate(new BigDecimal("32.5"));
        request.setRateType("MANUAL");
        return request;
    }

    @Test
    void list_returnsAllPairsFromMapper() {
        when(currencyPairMapper.findAll(null, null)).thenReturn(List.of(samplePair(1L, 3L, 2L, 1L)));

        List<CurrencyPair> result = currencyPairService.list(null, null);

        assertThat(result).hasSize(1);
    }

    @Test
    void list_filtersByBrandIdAndActive() {
        when(currencyPairMapper.findAll(3L, true)).thenReturn(List.of(samplePair(1L, 3L, 2L, 1L)));

        List<CurrencyPair> result = currencyPairService.list(3L, true);

        assertThat(result).hasSize(1);
        verify(currencyPairMapper).findAll(3L, true);
    }

    @Test
    void getById_returnsPair_whenFound() {
        when(currencyPairMapper.findById(1L)).thenReturn(samplePair(1L, 3L, 2L, 1L));

        CurrencyPair result = currencyPairService.getById(1L);

        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    void getById_throwsNotFound_whenMissing() {
        when(currencyPairMapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> currencyPairService.getById(999L))
                .isInstanceOf(CurrencyPairNotFoundException.class);
    }

    @Test
    void create_savesPair_whenValid() {
        CurrencyPairCreateRequest request = sampleCreateRequest(3L, 2L, 1L);

        when(brandMapper.findById(3L)).thenReturn(sampleBrand(3L));
        when(currencyMapper.findById(2L)).thenReturn(sampleCurrency(2L, "USD"));
        when(currencyMapper.findById(1L)).thenReturn(sampleCurrency(1L, "TWD"));
        when(currencyPairMapper.findByBrandBaseQuote(3L, 2L, 1L)).thenReturn(null);
        doAnswer(invocation -> {
            CurrencyPair toInsert = invocation.getArgument(0);
            toInsert.setId(1L);
            return 1;
        }).when(currencyPairMapper).insert(any(CurrencyPair.class));
        when(currencyPairMapper.findById(1L)).thenReturn(samplePair(1L, 3L, 2L, 1L));

        CurrencyPair result = currencyPairService.create(request);

        assertThat(result.getId()).isEqualTo(1L);
        verify(currencyPairMapper).insert(any(CurrencyPair.class));
    }

    @Test
    void create_defaultsActiveToTrue_whenNotProvided() {
        CurrencyPairCreateRequest request = sampleCreateRequest(3L, 2L, 1L);

        when(brandMapper.findById(3L)).thenReturn(sampleBrand(3L));
        when(currencyMapper.findById(2L)).thenReturn(sampleCurrency(2L, "USD"));
        when(currencyMapper.findById(1L)).thenReturn(sampleCurrency(1L, "TWD"));
        when(currencyPairMapper.findByBrandBaseQuote(3L, 2L, 1L)).thenReturn(null);
        doAnswer(invocation -> {
            CurrencyPair toInsert = invocation.getArgument(0);
            toInsert.setId(1L);
            return 1;
        }).when(currencyPairMapper).insert(any(CurrencyPair.class));
        when(currencyPairMapper.findById(1L)).thenReturn(samplePair(1L, 3L, 2L, 1L));

        currencyPairService.create(request);

        org.mockito.ArgumentCaptor<CurrencyPair> captor = org.mockito.ArgumentCaptor.forClass(CurrencyPair.class);
        verify(currencyPairMapper).insert(captor.capture());
        assertThat(captor.getValue().getActive()).isTrue();
    }

    @Test
    void create_throwsNotFound_whenBrandMissing() {
        CurrencyPairCreateRequest request = sampleCreateRequest(999L, 2L, 1L);

        when(brandMapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> currencyPairService.create(request))
                .isInstanceOf(BrandNotFoundException.class);
        verify(currencyPairMapper, never()).insert(any(CurrencyPair.class));
    }

    @Test
    void create_throwsNotFound_whenBaseCurrencyMissing() {
        CurrencyPairCreateRequest request = sampleCreateRequest(3L, 999L, 1L);

        when(brandMapper.findById(3L)).thenReturn(sampleBrand(3L));
        when(currencyMapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> currencyPairService.create(request))
                .isInstanceOf(CurrencyNotFoundException.class);
        verify(currencyPairMapper, never()).insert(any(CurrencyPair.class));
    }

    @Test
    void create_throws400_whenBaseEqualsQuote() {
        CurrencyPairCreateRequest request = sampleCreateRequest(3L, 1L, 1L);

        when(brandMapper.findById(3L)).thenReturn(sampleBrand(3L));
        when(currencyMapper.findById(1L)).thenReturn(sampleCurrency(1L, "TWD"));

        assertThatThrownBy(() -> currencyPairService.create(request))
                .isInstanceOf(InvalidCurrencyPairException.class);
        verify(currencyPairMapper, never()).insert(any(CurrencyPair.class));
    }

    @Test
    void create_throwsConflict_whenPairAlreadyExistsForBrand() {
        CurrencyPairCreateRequest request = sampleCreateRequest(3L, 2L, 1L);

        when(brandMapper.findById(3L)).thenReturn(sampleBrand(3L));
        when(currencyMapper.findById(2L)).thenReturn(sampleCurrency(2L, "USD"));
        when(currencyMapper.findById(1L)).thenReturn(sampleCurrency(1L, "TWD"));
        when(currencyPairMapper.findByBrandBaseQuote(3L, 2L, 1L)).thenReturn(samplePair(5L, 3L, 2L, 1L));

        assertThatThrownBy(() -> currencyPairService.create(request))
                .isInstanceOf(CurrencyPairExistsException.class);
        verify(currencyPairMapper, never()).insert(any(CurrencyPair.class));
    }

    @Test
    void update_appliesPartialChanges_whenFound() {
        CurrencyPair existing = samplePair(1L, 3L, 2L, 1L);
        when(currencyPairMapper.findById(1L)).thenReturn(existing);
        when(currencyPairMapper.findByBrandBaseQuote(3L, 2L, 1L)).thenReturn(existing);

        CurrencyPairUpdateRequest request = new CurrencyPairUpdateRequest();
        request.setRate(new BigDecimal("35.0"));

        CurrencyPair result = currencyPairService.update(1L, request);

        assertThat(result.getRate()).isEqualByComparingTo("35.0");
        verify(currencyPairMapper).update(any(CurrencyPair.class));
    }

    @Test
    void update_throwsNotFound_whenMissing() {
        when(currencyPairMapper.findById(999L)).thenReturn(null);

        CurrencyPairUpdateRequest request = new CurrencyPairUpdateRequest();
        request.setRate(new BigDecimal("35.0"));

        assertThatThrownBy(() -> currencyPairService.update(999L, request))
                .isInstanceOf(CurrencyPairNotFoundException.class);
        verify(currencyPairMapper, never()).update(any(CurrencyPair.class));
    }

    @Test
    void update_throwsConflict_whenCollidesWithAnotherRow() {
        CurrencyPair existing = samplePair(1L, 3L, 2L, 1L);
        CurrencyPair other = samplePair(2L, 3L, 2L, 1L);
        when(currencyPairMapper.findById(1L)).thenReturn(existing);
        when(currencyPairMapper.findByBrandBaseQuote(3L, 2L, 1L)).thenReturn(other);

        CurrencyPairUpdateRequest request = new CurrencyPairUpdateRequest();
        request.setRate(new BigDecimal("35.0"));

        assertThatThrownBy(() -> currencyPairService.update(1L, request))
                .isInstanceOf(CurrencyPairExistsException.class);
        verify(currencyPairMapper, never()).update(any(CurrencyPair.class));
    }

    @Test
    void update_throws400_whenNewBaseEqualsQuote() {
        CurrencyPair existing = samplePair(1L, 3L, 2L, 1L);
        when(currencyPairMapper.findById(1L)).thenReturn(existing);
        when(currencyMapper.findById(1L)).thenReturn(sampleCurrency(1L, "TWD"));

        CurrencyPairUpdateRequest request = new CurrencyPairUpdateRequest();
        request.setBaseCurrencyId(1L);

        assertThatThrownBy(() -> currencyPairService.update(1L, request))
                .isInstanceOf(InvalidCurrencyPairException.class);
        verify(currencyPairMapper, never()).update(any(CurrencyPair.class));
    }

    @Test
    void delete_removesPair_whenFound() {
        when(currencyPairMapper.findById(1L)).thenReturn(samplePair(1L, 3L, 2L, 1L));

        currencyPairService.delete(1L);

        verify(currencyPairMapper).deleteById(1L);
    }

    @Test
    void delete_throwsNotFound_whenMissing() {
        when(currencyPairMapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> currencyPairService.delete(999L))
                .isInstanceOf(CurrencyPairNotFoundException.class);
        verify(currencyPairMapper, never()).deleteById(999L);
    }

    // Rate/rateType rule tests (delta)

    @Test
    void create_throws400_whenRateTypeManualAndRateMissing() {
        CurrencyPairCreateRequest request = new CurrencyPairCreateRequest();
        request.setBrandId(3L);
        request.setBaseCurrencyId(2L);
        request.setQuoteCurrencyId(1L);
        request.setRateType("MANUAL");
        request.setRate(null);

        when(brandMapper.findById(3L)).thenReturn(sampleBrand(3L));
        when(currencyMapper.findById(2L)).thenReturn(sampleCurrency(2L, "USD"));
        when(currencyMapper.findById(1L)).thenReturn(sampleCurrency(1L, "TWD"));
        when(currencyPairMapper.findByBrandBaseQuote(3L, 2L, 1L)).thenReturn(null);

        assertThatThrownBy(() -> currencyPairService.create(request))
                .isInstanceOf(InvalidCurrencyPairException.class)
                .hasMessageContaining("rate is required and must be greater than 0 when rateType is MANUAL");
        verify(currencyPairMapper, never()).insert(any(CurrencyPair.class));
    }

    @Test
    void create_throws400_whenRateTypeManualAndRateZero() {
        CurrencyPairCreateRequest request = sampleCreateRequest(3L, 2L, 1L);
        request.setRate(BigDecimal.ZERO);

        when(brandMapper.findById(3L)).thenReturn(sampleBrand(3L));
        when(currencyMapper.findById(2L)).thenReturn(sampleCurrency(2L, "USD"));
        when(currencyMapper.findById(1L)).thenReturn(sampleCurrency(1L, "TWD"));
        when(currencyPairMapper.findByBrandBaseQuote(3L, 2L, 1L)).thenReturn(null);

        assertThatThrownBy(() -> currencyPairService.create(request))
                .isInstanceOf(InvalidCurrencyPairException.class)
                .hasMessageContaining("rate is required and must be greater than 0 when rateType is MANUAL");
        verify(currencyPairMapper, never()).insert(any(CurrencyPair.class));
    }

    @Test
    void create_throws400_whenRateTypeManualAndRateNegative() {
        CurrencyPairCreateRequest request = sampleCreateRequest(3L, 2L, 1L);
        request.setRate(new BigDecimal("-1.0"));

        when(brandMapper.findById(3L)).thenReturn(sampleBrand(3L));
        when(currencyMapper.findById(2L)).thenReturn(sampleCurrency(2L, "USD"));
        when(currencyMapper.findById(1L)).thenReturn(sampleCurrency(1L, "TWD"));
        when(currencyPairMapper.findByBrandBaseQuote(3L, 2L, 1L)).thenReturn(null);

        assertThatThrownBy(() -> currencyPairService.create(request))
                .isInstanceOf(InvalidCurrencyPairException.class)
                .hasMessageContaining("rate is required and must be greater than 0 when rateType is MANUAL");
        verify(currencyPairMapper, never()).insert(any(CurrencyPair.class));
    }

    @Test
    void create_forcesRateToNull_whenRateTypeAutoWithRateSupplied() {
        CurrencyPairCreateRequest request = new CurrencyPairCreateRequest();
        request.setBrandId(3L);
        request.setBaseCurrencyId(2L);
        request.setQuoteCurrencyId(1L);
        request.setRateType("AUTO");
        request.setRate(new BigDecimal("100.0"));

        when(brandMapper.findById(3L)).thenReturn(sampleBrand(3L));
        when(currencyMapper.findById(2L)).thenReturn(sampleCurrency(2L, "USD"));
        when(currencyMapper.findById(1L)).thenReturn(sampleCurrency(1L, "TWD"));
        when(currencyPairMapper.findByBrandBaseQuote(3L, 2L, 1L)).thenReturn(null);
        doAnswer(invocation -> {
            CurrencyPair toInsert = invocation.getArgument(0);
            toInsert.setId(1L);
            return 1;
        }).when(currencyPairMapper).insert(any(CurrencyPair.class));
        CurrencyPair inserted = new CurrencyPair();
        inserted.setId(1L);
        inserted.setRate(null);
        inserted.setRateType("AUTO");
        when(currencyPairMapper.findById(1L)).thenReturn(inserted);

        CurrencyPair result = currencyPairService.create(request);

        org.mockito.ArgumentCaptor<CurrencyPair> captor = org.mockito.ArgumentCaptor.forClass(CurrencyPair.class);
        verify(currencyPairMapper).insert(captor.capture());
        assertThat(captor.getValue().getRate()).isNull();
        assertThat(result.getRate()).isNull();
    }

    @Test
    void create_forcesRateToNull_whenRateTypeAutoWithoutRate() {
        CurrencyPairCreateRequest request = new CurrencyPairCreateRequest();
        request.setBrandId(3L);
        request.setBaseCurrencyId(2L);
        request.setQuoteCurrencyId(1L);
        request.setRateType("AUTO");
        request.setRate(null);

        when(brandMapper.findById(3L)).thenReturn(sampleBrand(3L));
        when(currencyMapper.findById(2L)).thenReturn(sampleCurrency(2L, "USD"));
        when(currencyMapper.findById(1L)).thenReturn(sampleCurrency(1L, "TWD"));
        when(currencyPairMapper.findByBrandBaseQuote(3L, 2L, 1L)).thenReturn(null);
        doAnswer(invocation -> {
            CurrencyPair toInsert = invocation.getArgument(0);
            toInsert.setId(1L);
            return 1;
        }).when(currencyPairMapper).insert(any(CurrencyPair.class));
        CurrencyPair inserted = new CurrencyPair();
        inserted.setId(1L);
        inserted.setRate(null);
        inserted.setRateType("AUTO");
        when(currencyPairMapper.findById(1L)).thenReturn(inserted);

        CurrencyPair result = currencyPairService.create(request);

        org.mockito.ArgumentCaptor<CurrencyPair> captor = org.mockito.ArgumentCaptor.forClass(CurrencyPair.class);
        verify(currencyPairMapper).insert(captor.capture());
        assertThat(captor.getValue().getRate()).isNull();
        assertThat(result.getRate()).isNull();
    }

    @Test
    void update_clearsRate_whenSwitchingToAuto() {
        CurrencyPair existing = samplePair(1L, 3L, 2L, 1L);
        existing.setRate(new BigDecimal("50.0"));
        existing.setRateType("MANUAL");
        when(currencyPairMapper.findById(1L)).thenReturn(existing).thenReturn(existing);
        when(currencyPairMapper.findByBrandBaseQuote(3L, 2L, 1L)).thenReturn(existing);

        CurrencyPairUpdateRequest request = new CurrencyPairUpdateRequest();
        request.setRateType("AUTO");

        currencyPairService.update(1L, request);

        org.mockito.ArgumentCaptor<CurrencyPair> captor = org.mockito.ArgumentCaptor.forClass(CurrencyPair.class);
        verify(currencyPairMapper).update(captor.capture());
        assertThat(captor.getValue().getRate()).isNull();
    }

    @Test
    void update_clearsRate_whenSwitchingToAutoEvenIfRateSupplied() {
        CurrencyPair existing = samplePair(1L, 3L, 2L, 1L);
        existing.setRate(new BigDecimal("50.0"));
        existing.setRateType("MANUAL");
        when(currencyPairMapper.findById(1L)).thenReturn(existing).thenReturn(existing);
        when(currencyPairMapper.findByBrandBaseQuote(3L, 2L, 1L)).thenReturn(existing);

        CurrencyPairUpdateRequest request = new CurrencyPairUpdateRequest();
        request.setRateType("AUTO");
        request.setRate(new BigDecimal("999.0"));

        currencyPairService.update(1L, request);

        org.mockito.ArgumentCaptor<CurrencyPair> captor = org.mockito.ArgumentCaptor.forClass(CurrencyPair.class);
        verify(currencyPairMapper).update(captor.capture());
        assertThat(captor.getValue().getRate()).isNull();
    }

    @Test
    void update_throws400_whenSwitchingToManualWithoutRate() {
        CurrencyPair existing = samplePair(1L, 3L, 2L, 1L);
        existing.setRate(null);
        existing.setRateType("AUTO");
        when(currencyPairMapper.findById(1L)).thenReturn(existing);

        CurrencyPairUpdateRequest request = new CurrencyPairUpdateRequest();
        request.setRateType("MANUAL");

        assertThatThrownBy(() -> currencyPairService.update(1L, request))
                .isInstanceOf(InvalidCurrencyPairException.class)
                .hasMessageContaining("rate is required and must be greater than 0 when rateType is MANUAL");
        verify(currencyPairMapper, never()).update(any(CurrencyPair.class));
    }

    @Test
    void update_succeeds_whenSwitchingToManualWithValidRate() {
        CurrencyPair existing = samplePair(1L, 3L, 2L, 1L);
        existing.setRate(null);
        existing.setRateType("AUTO");
        when(currencyPairMapper.findById(1L)).thenReturn(existing).thenReturn(existing);
        when(currencyPairMapper.findByBrandBaseQuote(3L, 2L, 1L)).thenReturn(existing);

        CurrencyPairUpdateRequest request = new CurrencyPairUpdateRequest();
        request.setRateType("MANUAL");
        request.setRate(new BigDecimal("42.0"));

        currencyPairService.update(1L, request);

        org.mockito.ArgumentCaptor<CurrencyPair> captor = org.mockito.ArgumentCaptor.forClass(CurrencyPair.class);
        verify(currencyPairMapper).update(captor.capture());
        assertThat(captor.getValue().getRate()).isEqualByComparingTo("42.0");
    }
}
