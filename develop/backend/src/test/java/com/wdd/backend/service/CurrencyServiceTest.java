package com.wdd.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
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

import com.wdd.backend.dto.CurrencyCreateRequest;
import com.wdd.backend.dto.CurrencyResponse;
import com.wdd.backend.dto.CurrencyUpdateRequest;
import com.wdd.backend.exception.CurrencyCodeExistsException;
import com.wdd.backend.exception.CurrencyInUseException;
import com.wdd.backend.exception.CurrencyNotFoundException;
import com.wdd.backend.mapper.CurrencyMapper;
import com.wdd.backend.mapper.CurrencyPairMapper;
import com.wdd.backend.model.Currency;

@ExtendWith(MockitoExtension.class)
class CurrencyServiceTest {

    @Mock
    private CurrencyMapper currencyMapper;

    @Mock
    private CurrencyPairMapper currencyPairMapper;

    private CurrencyService currencyService;

    @BeforeEach
    void setUp() {
        currencyService = new CurrencyService(currencyMapper, currencyPairMapper);
    }

    private Currency sampleCurrency() {
        Currency currency = new Currency();
        currency.setId(1L);
        currency.setCode("TWD");
        currency.setName("New Taiwan Dollar");
        currency.setNameZh("新台幣");
        currency.setSymbol("NT$");
        currency.setDecimalPlaces(0);
        currency.setCreatedAt(LocalDateTime.now());
        currency.setUpdatedAt(LocalDateTime.now());
        return currency;
    }

    @Test
    void list_returnsAllCurrencies() {
        when(currencyMapper.findAll()).thenReturn(List.of(sampleCurrency()));

        List<CurrencyResponse> result = currencyService.list();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCode()).isEqualTo("TWD");
    }

    @Test
    void list_returnsEmptyListWhenNoCurrencies() {
        when(currencyMapper.findAll()).thenReturn(List.of());

        List<CurrencyResponse> result = currencyService.list();

        assertThat(result).isEmpty();
    }

    @Test
    void getById_returnsCurrencyWhenFound() {
        when(currencyMapper.findById(1L)).thenReturn(Optional.of(sampleCurrency()));

        CurrencyResponse result = currencyService.getById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getCode()).isEqualTo("TWD");
    }

    @Test
    void getById_throwsNotFoundWhenMissing() {
        when(currencyMapper.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> currencyService.getById(999L))
                .isInstanceOf(CurrencyNotFoundException.class);
    }

    @Test
    void create_createsAndReturnsCurrency() {
        CurrencyCreateRequest request = new CurrencyCreateRequest();
        request.setCode("KRW");
        request.setName("South Korean Won");
        request.setNameZh("韓元");
        request.setSymbol("₩");
        request.setDecimalPlaces(0);

        when(currencyMapper.findByCode("KRW")).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            Currency c = invocation.getArgument(0);
            c.setId(2L);
            return 1;
        }).when(currencyMapper).insert(any(Currency.class));

        Currency created = sampleCurrency();
        created.setId(2L);
        created.setCode("KRW");
        created.setName("South Korean Won");
        created.setNameZh("韓元");
        created.setSymbol("₩");
        created.setDecimalPlaces(0);
        when(currencyMapper.findById(2L)).thenReturn(Optional.of(created));

        CurrencyResponse result = currencyService.create(request);

        assertThat(result.getId()).isEqualTo(2L);
        assertThat(result.getCode()).isEqualTo("KRW");
    }

    @Test
    void create_throwsConflictWhenCodeExists() {
        CurrencyCreateRequest request = new CurrencyCreateRequest();
        request.setCode("TWD");
        request.setName("New Taiwan Dollar");
        request.setDecimalPlaces(0);

        when(currencyMapper.findByCode("TWD")).thenReturn(Optional.of(sampleCurrency()));

        assertThatThrownBy(() -> currencyService.create(request))
                .isInstanceOf(CurrencyCodeExistsException.class);

        verify(currencyMapper, never()).insert(any(Currency.class));
    }

    @Test
    void update_updatesAndReturnsCurrency() {
        Currency existing = sampleCurrency();
        when(currencyMapper.findById(1L)).thenReturn(Optional.of(existing));

        CurrencyUpdateRequest request = new CurrencyUpdateRequest();
        request.setName("Updated Name");

        Currency updated = sampleCurrency();
        updated.setName("Updated Name");
        when(currencyMapper.update(any(Currency.class))).thenReturn(1);

        // second findById call after update returns the updated entity
        when(currencyMapper.findById(1L)).thenReturn(Optional.of(existing), Optional.of(updated));

        CurrencyResponse result = currencyService.update(1L, request);

        assertThat(result.getName()).isEqualTo("Updated Name");
    }

    @Test
    void update_throwsNotFoundWhenMissing() {
        when(currencyMapper.findById(999L)).thenReturn(Optional.empty());

        CurrencyUpdateRequest request = new CurrencyUpdateRequest();
        request.setName("Doesn't matter");

        assertThatThrownBy(() -> currencyService.update(999L, request))
                .isInstanceOf(CurrencyNotFoundException.class);
    }

    @Test
    void delete_deletesWhenFound() {
        when(currencyMapper.findById(1L)).thenReturn(Optional.of(sampleCurrency()));
        when(currencyPairMapper.existsByCurrencyId(1L)).thenReturn(false);

        currencyService.delete(1L);

        verify(currencyMapper).deleteById(1L);
    }

    @Test
    void delete_throwsNotFoundWhenMissing() {
        when(currencyMapper.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> currencyService.delete(999L))
                .isInstanceOf(CurrencyNotFoundException.class);

        verify(currencyMapper, never()).deleteById(eq(999L));
    }

    @Test
    void delete_throwsConflict_whenReferencedByCurrencyPair() {
        when(currencyMapper.findById(1L)).thenReturn(Optional.of(sampleCurrency()));
        when(currencyPairMapper.existsByCurrencyId(1L)).thenReturn(true);

        assertThatThrownBy(() -> currencyService.delete(1L))
                .isInstanceOf(CurrencyInUseException.class);

        verify(currencyMapper, never()).deleteById(eq(1L));
    }
}
