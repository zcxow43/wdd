package com.wdd.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

import com.wdd.backend.dto.CurrencyPairCreateRequest;
import com.wdd.backend.dto.CurrencyPairDefinitionCreateRequest;
import com.wdd.backend.dto.CurrencyPairDefinitionResponse;
import com.wdd.backend.dto.CurrencyPairDefinitionUpdateRequest;
import com.wdd.backend.dto.CurrencyPairResponse;
import com.wdd.backend.exception.CurrencyNotFoundException;
import com.wdd.backend.exception.CurrencyPairDefinitionExistsException;
import com.wdd.backend.exception.CurrencyPairDefinitionInUseException;
import com.wdd.backend.exception.CurrencyPairDefinitionNotFoundException;
import com.wdd.backend.exception.InvalidCurrencyPairException;
import com.wdd.backend.mapper.BrandMapper;
import com.wdd.backend.mapper.CurrencyMapper;
import com.wdd.backend.mapper.CurrencyPairDefinitionMapper;
import com.wdd.backend.mapper.CurrencyPairMapper;
import com.wdd.backend.model.Brand;
import com.wdd.backend.model.Currency;
import com.wdd.backend.model.CurrencyPair;
import com.wdd.backend.model.CurrencyPairDefinition;

@ExtendWith(MockitoExtension.class)
class CurrencyPairDefinitionServiceTest {

    @Mock
    private CurrencyPairDefinitionMapper currencyPairDefinitionMapper;

    @Mock
    private CurrencyPairMapper currencyPairMapper;

    @Mock
    private BrandMapper brandMapper;

    @Mock
    private CurrencyMapper currencyMapper;

    @Mock
    private CurrencyPairService currencyPairService;

    private CurrencyPairDefinitionService service;

    @BeforeEach
    void setUp() {
        service = new CurrencyPairDefinitionService(currencyPairDefinitionMapper, currencyPairMapper, brandMapper,
                currencyMapper, currencyPairService);
    }

    private Currency sampleCurrency(Long id, String code) {
        Currency currency = new Currency();
        currency.setId(id);
        currency.setCode(code);
        currency.setName(code);
        currency.setDecimalPlaces(2);
        return currency;
    }

    private Brand sampleBrand(Long id, String code) {
        Brand brand = new Brand();
        brand.setId(id);
        brand.setCode(code);
        brand.setName(code);
        brand.setActive(true);
        return brand;
    }

    private CurrencyPairDefinition sampleDefinition() {
        CurrencyPairDefinition definition = new CurrencyPairDefinition();
        definition.setId(1L);
        definition.setBaseCurrencyId(2L);
        definition.setBaseCurrencyCode("USD");
        definition.setQuoteCurrencyId(3L);
        definition.setQuoteCurrencyCode("JPY");
        definition.setForwardPrecision(2);
        definition.setReversePrecision(5);
        definition.setCreatedAt(LocalDateTime.now());
        definition.setUpdatedAt(LocalDateTime.now());
        return definition;
    }

    private CurrencyPairDefinitionCreateRequest createRequest() {
        CurrencyPairDefinitionCreateRequest request = new CurrencyPairDefinitionCreateRequest();
        request.setBaseCurrencyId(2L);
        request.setQuoteCurrencyId(3L);
        request.setForwardPrecision(2);
        request.setReversePrecision(5);
        return request;
    }

    private void stubCurrenciesExist() {
        when(currencyMapper.findById(2L)).thenReturn(Optional.of(sampleCurrency(2L, "USD")));
        when(currencyMapper.findById(3L)).thenReturn(Optional.of(sampleCurrency(3L, "JPY")));
    }

    private CurrencyPair activeCurrencyPair(Long brandId, String brandCode) {
        CurrencyPair pair = new CurrencyPair();
        pair.setId(100L + brandId);
        pair.setBrandId(brandId);
        pair.setBrandCode(brandCode);
        pair.setBaseCurrencyId(2L);
        pair.setQuoteCurrencyId(3L);
        pair.setRateType("AUTO");
        pair.setActive(true);
        return pair;
    }

    // ---------- list / getById ----------

    @Test
    void list_returnsAllDefinitions() {
        when(currencyPairDefinitionMapper.findAll(isNull(), isNull())).thenReturn(List.of(sampleDefinition()));

        List<CurrencyPairDefinitionResponse> result = service.list(null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getBaseCurrencyCode()).isEqualTo("USD");
        assertThat(result.get(0).getQuoteCurrencyCode()).isEqualTo("JPY");
    }

    @Test
    void list_filtersByBaseAndQuote() {
        when(currencyPairDefinitionMapper.findAll(eq(2L), eq(3L))).thenReturn(List.of(sampleDefinition()));

        List<CurrencyPairDefinitionResponse> result = service.list(2L, 3L);

        assertThat(result).hasSize(1);
        verify(currencyPairDefinitionMapper).findAll(eq(2L), eq(3L));
    }

    @Test
    void getById_returnsDefinitionWhenFound() {
        when(currencyPairDefinitionMapper.findById(1L)).thenReturn(Optional.of(sampleDefinition()));

        CurrencyPairDefinitionResponse result = service.getById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getForwardPrecision()).isEqualTo(2);
        assertThat(result.getReversePrecision()).isEqualTo(5);
    }

    @Test
    void getById_throwsNotFoundWhenMissing() {
        when(currencyPairDefinitionMapper.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(999L))
                .isInstanceOf(CurrencyPairDefinitionNotFoundException.class);
    }

    // ---------- create ----------

    @Test
    void create_throwsNotFoundWhenBaseCurrencyMissing() {
        when(currencyMapper.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(createRequest()))
                .isInstanceOf(CurrencyNotFoundException.class);

        verify(currencyPairDefinitionMapper, never()).insert(any(CurrencyPairDefinition.class));
    }

    @Test
    void create_throwsNotFoundWhenQuoteCurrencyMissing() {
        when(currencyMapper.findById(2L)).thenReturn(Optional.of(sampleCurrency(2L, "USD")));
        when(currencyMapper.findById(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(createRequest()))
                .isInstanceOf(CurrencyNotFoundException.class);

        verify(currencyPairDefinitionMapper, never()).insert(any(CurrencyPairDefinition.class));
    }

    @Test
    void create_throwsBadRequestWhenBaseEqualsQuote() {
        when(currencyMapper.findById(2L)).thenReturn(Optional.of(sampleCurrency(2L, "USD")));

        CurrencyPairDefinitionCreateRequest request = createRequest();
        request.setQuoteCurrencyId(2L);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(InvalidCurrencyPairException.class);

        verify(currencyPairDefinitionMapper, never()).insert(any(CurrencyPairDefinition.class));
    }

    @Test
    void create_throwsConflictWhenExactDirectionExists() {
        stubCurrenciesExist();
        when(currencyPairDefinitionMapper.findByEitherDirection(2L, 3L)).thenReturn(Optional.of(sampleDefinition()));

        assertThatThrownBy(() -> service.create(createRequest()))
                .isInstanceOf(CurrencyPairDefinitionExistsException.class);

        verify(currencyPairDefinitionMapper, never()).insert(any(CurrencyPairDefinition.class));
    }

    @Test
    void create_throwsConflictWhenReverseDirectionExists() {
        // Same either-direction pre-check catches the reverse direction too: requesting
        // (base=3/JPY, quote=2/USD) is rejected because a definition for (base=2, quote=3)
        // already exists.
        CurrencyPairDefinitionCreateRequest reverseRequest = createRequest();
        reverseRequest.setBaseCurrencyId(3L);
        reverseRequest.setQuoteCurrencyId(2L);
        when(currencyMapper.findById(3L)).thenReturn(Optional.of(sampleCurrency(3L, "JPY")));
        when(currencyMapper.findById(2L)).thenReturn(Optional.of(sampleCurrency(2L, "USD")));
        when(currencyPairDefinitionMapper.findByEitherDirection(3L, 2L)).thenReturn(Optional.of(sampleDefinition()));

        assertThatThrownBy(() -> service.create(reverseRequest))
                .isInstanceOf(CurrencyPairDefinitionExistsException.class);

        verify(currencyPairDefinitionMapper, never()).insert(any(CurrencyPairDefinition.class));
    }

    @Test
    void create_insertsDefinitionAndFansOutToAllBrands() {
        stubCurrenciesExist();
        when(currencyPairDefinitionMapper.findByEitherDirection(2L, 3L)).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            CurrencyPairDefinition d = invocation.getArgument(0);
            d.setId(10L);
            return 1;
        }).when(currencyPairDefinitionMapper).insert(any(CurrencyPairDefinition.class));
        when(currencyPairDefinitionMapper.findById(10L)).thenReturn(Optional.of(sampleDefinition()));

        Brand brandAu = sampleBrand(1L, "AU");
        Brand brandPug = sampleBrand(2L, "PUG");
        when(brandMapper.findAll(null)).thenReturn(List.of(brandAu, brandPug));
        when(currencyPairMapper.findByBrandBaseQuote(1L, 2L, 3L, null)).thenReturn(Optional.empty());
        when(currencyPairMapper.findByBrandBaseQuote(2L, 2L, 3L, null)).thenReturn(Optional.empty());

        CurrencyPairDefinitionResponse result = service.create(createRequest());

        assertThat(result).isNotNull();
        verify(currencyPairDefinitionMapper).insert(any(CurrencyPairDefinition.class));
        verify(currencyPairService, times(2)).create(any(CurrencyPairCreateRequest.class));
        verify(currencyPairService).create(argThatMatchesFanOut(1L));
        verify(currencyPairService).create(argThatMatchesFanOut(2L));
    }

    @Test
    void create_skipsBrandThatAlreadyHasLiveRow() {
        stubCurrenciesExist();
        when(currencyPairDefinitionMapper.findByEitherDirection(2L, 3L)).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            CurrencyPairDefinition d = invocation.getArgument(0);
            d.setId(11L);
            return 1;
        }).when(currencyPairDefinitionMapper).insert(any(CurrencyPairDefinition.class));
        when(currencyPairDefinitionMapper.findById(11L)).thenReturn(Optional.of(sampleDefinition()));

        Brand brandAu = sampleBrand(1L, "AU");
        Brand brandPug = sampleBrand(2L, "PUG");
        when(brandMapper.findAll(null)).thenReturn(List.of(brandAu, brandPug));
        // PUG (brand 2) already has a live row for this exact triple — must be skipped, left
        // completely untouched.
        CurrencyPair existingPugRow = new CurrencyPair();
        existingPugRow.setId(999L);
        when(currencyPairMapper.findByBrandBaseQuote(1L, 2L, 3L, null)).thenReturn(Optional.empty());
        when(currencyPairMapper.findByBrandBaseQuote(2L, 2L, 3L, null)).thenReturn(Optional.of(existingPugRow));

        service.create(createRequest());

        verify(currencyPairService, times(1)).create(any(CurrencyPairCreateRequest.class));
        verify(currencyPairService).create(argThatMatchesFanOut(1L));
        verify(currencyPairService, never()).create(argThatMatchesFanOut(2L));
    }

    private CurrencyPairCreateRequest argThatMatchesFanOut(Long brandId) {
        return org.mockito.ArgumentMatchers.argThat(request -> request != null
                && brandId.equals(request.getBrandId())
                && Long.valueOf(2L).equals(request.getBaseCurrencyId())
                && Long.valueOf(3L).equals(request.getQuoteCurrencyId())
                && "AUTO".equals(request.getRateType())
                && request.getRate() == null
                && Boolean.TRUE.equals(request.getActive()));
    }

    @Test
    void create_doesNotPersistDefinition_whenFanOutFails() {
        stubCurrenciesExist();
        when(currencyPairDefinitionMapper.findByEitherDirection(2L, 3L)).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            CurrencyPairDefinition d = invocation.getArgument(0);
            d.setId(12L);
            return 1;
        }).when(currencyPairDefinitionMapper).insert(any(CurrencyPairDefinition.class));

        Brand brandAu = sampleBrand(1L, "AU");
        when(brandMapper.findAll(null)).thenReturn(List.of(brandAu));
        when(currencyPairMapper.findByBrandBaseQuote(1L, 2L, 3L, null)).thenReturn(Optional.empty());
        when(currencyPairService.create(any(CurrencyPairCreateRequest.class)))
                .thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> service.create(createRequest()))
                .isInstanceOf(RuntimeException.class);

        // insert was called (would have run inside the same @Transactional method, which the
        // Spring transaction manager rolls back on exception in a real transactional context);
        // findById re-fetch never happens since the fan-out failed before reaching it.
        verify(currencyPairDefinitionMapper, never()).findById(12L);
    }

    // ---------- update ----------

    @Test
    void update_updatesPrecisionAndReturnsDefinition() {
        CurrencyPairDefinition existing = sampleDefinition();
        when(currencyPairDefinitionMapper.findById(1L)).thenReturn(Optional.of(existing));

        CurrencyPairDefinitionUpdateRequest request = new CurrencyPairDefinitionUpdateRequest();
        request.setForwardPrecision(4);
        request.setReversePrecision(6);

        CurrencyPairDefinition updated = sampleDefinition();
        updated.setForwardPrecision(4);
        updated.setReversePrecision(6);
        when(currencyPairDefinitionMapper.findById(1L)).thenReturn(Optional.of(existing), Optional.of(updated));

        CurrencyPairDefinitionResponse result = service.update(1L, request);

        assertThat(result.getForwardPrecision()).isEqualTo(4);
        assertThat(result.getReversePrecision()).isEqualTo(6);
        verify(currencyPairDefinitionMapper).update(any(CurrencyPairDefinition.class));
    }

    @Test
    void update_throwsNotFoundWhenMissing() {
        when(currencyPairDefinitionMapper.findById(999L)).thenReturn(Optional.empty());

        CurrencyPairDefinitionUpdateRequest request = new CurrencyPairDefinitionUpdateRequest();
        request.setForwardPrecision(2);
        request.setReversePrecision(2);

        assertThatThrownBy(() -> service.update(999L, request))
                .isInstanceOf(CurrencyPairDefinitionNotFoundException.class);

        verify(currencyPairDefinitionMapper, never()).update(any(CurrencyPairDefinition.class));
    }

    // ---------- delete ----------

    @Test
    void delete_throwsNotFoundWhenMissing() {
        when(currencyPairDefinitionMapper.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(999L))
                .isInstanceOf(CurrencyPairDefinitionNotFoundException.class);

        verify(currencyPairDefinitionMapper, never()).deleteById(anyLong());
    }

    @Test
    void delete_blockedWithOneActiveBrand() {
        when(currencyPairDefinitionMapper.findById(1L)).thenReturn(Optional.of(sampleDefinition()));
        when(currencyPairMapper.findActiveByBaseQuote(2L, 3L)).thenReturn(List.of(activeCurrencyPair(2L, "PUG")));

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(CurrencyPairDefinitionInUseException.class)
                .satisfies(ex -> {
                    CurrencyPairDefinitionInUseException e = (CurrencyPairDefinitionInUseException) ex;
                    assertThat(e.getActiveBrandCodes()).containsExactly("PUG");
                });

        verify(currencyPairDefinitionMapper, never()).deleteById(anyLong());
    }

    @Test
    void delete_blockedWithMultipleActiveBrands() {
        when(currencyPairDefinitionMapper.findById(1L)).thenReturn(Optional.of(sampleDefinition()));
        when(currencyPairMapper.findActiveByBaseQuote(2L, 3L))
                .thenReturn(List.of(activeCurrencyPair(1L, "AU"), activeCurrencyPair(2L, "PUG")));

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(CurrencyPairDefinitionInUseException.class)
                .satisfies(ex -> {
                    CurrencyPairDefinitionInUseException e = (CurrencyPairDefinitionInUseException) ex;
                    assertThat(e.getActiveBrandCodes()).containsExactly("AU", "PUG");
                });

        verify(currencyPairDefinitionMapper, never()).deleteById(anyLong());
    }

    @Test
    void delete_allowedOnceAllInactive() {
        when(currencyPairDefinitionMapper.findById(1L)).thenReturn(Optional.of(sampleDefinition()));
        when(currencyPairMapper.findActiveByBaseQuote(2L, 3L)).thenReturn(List.of());

        service.delete(1L);

        verify(currencyPairDefinitionMapper).deleteById(1L);
    }

    @Test
    void delete_allowedWithZeroRows() {
        // Zero currency_pair rows exist at all for this pair (e.g. all were independently
        // deleted) — findActiveByBaseQuote correctly returns an empty list, which never blocks
        // deletion; only a live, active row does.
        when(currencyPairDefinitionMapper.findById(1L)).thenReturn(Optional.of(sampleDefinition()));
        when(currencyPairMapper.findActiveByBaseQuote(2L, 3L)).thenReturn(List.of());

        service.delete(1L);

        verify(currencyPairDefinitionMapper).deleteById(1L);
    }
}
