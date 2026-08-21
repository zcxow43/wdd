package com.wdd.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wdd.backend.dto.Currency;
import com.wdd.backend.dto.CurrencyCreateRequest;
import com.wdd.backend.dto.CurrencyResponse;
import com.wdd.backend.dto.CurrencyUpdateRequest;
import com.wdd.backend.exception.CurrencyCodeConflictException;
import com.wdd.backend.exception.CurrencyNotFoundException;
import com.wdd.backend.exception.InvalidRequestException;
import com.wdd.backend.mapper.CurrencyMapper;

class CurrencyServiceTest {

    private CurrencyMapper currencyMapper;
    private CurrencyService currencyService;

    @BeforeEach
    void setUp() {
        currencyMapper = mock(CurrencyMapper.class);
        currencyService = new CurrencyService(currencyMapper);
    }

    private static Currency sampleCurrency(Long id, String code, String name, String symbol, Integer decimalPlaces) {
        Currency currency = new Currency();
        currency.setId(id);
        currency.setCode(code);
        currency.setName(name);
        currency.setSymbol(symbol);
        currency.setDecimalPlaces(decimalPlaces);
        currency.setCreatedAt(LocalDateTime.now());
        currency.setUpdatedAt(LocalDateTime.now());
        return currency;
    }

    @Test
    void findByIdThrowsWhenCurrencyMissing() {
        when(currencyMapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> currencyService.findById(999L))
                .isInstanceOf(CurrencyNotFoundException.class);
    }

    @Test
    void findByIdReturnsMappedResponse() {
        when(currencyMapper.findById(1L)).thenReturn(sampleCurrency(1L, "USD", "US Dollar", "$", 2));

        CurrencyResponse response = currencyService.findById(1L);

        assertThat(response.getCode()).isEqualTo("USD");
        assertThat(response.getName()).isEqualTo("US Dollar");
        assertThat(response.getSymbol()).isEqualTo("$");
        assertThat(response.getDecimalPlaces()).isEqualTo(2);
    }

    @Test
    void createRejectsInvalidCodeFormat() {
        CurrencyCreateRequest request = new CurrencyCreateRequest("usd", "US Dollar", "$", 2);

        assertThatThrownBy(() -> currencyService.create(request))
                .isInstanceOf(InvalidRequestException.class);

        verify(currencyMapper, never()).insert(any());
    }

    @Test
    void createRejectsMissingName() {
        CurrencyCreateRequest request = new CurrencyCreateRequest("USD", " ", "$", 2);

        assertThatThrownBy(() -> currencyService.create(request))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void createRejectsMissingSymbol() {
        CurrencyCreateRequest request = new CurrencyCreateRequest("USD", "US Dollar", "", 2);

        assertThatThrownBy(() -> currencyService.create(request))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void createRejectsDecimalPlacesOutOfRange() {
        CurrencyCreateRequest request = new CurrencyCreateRequest("USD", "US Dollar", "$", 9);

        assertThatThrownBy(() -> currencyService.create(request))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void createRejectsDuplicateCode() {
        CurrencyCreateRequest request = new CurrencyCreateRequest("USD", "US Dollar", "$", 2);
        when(currencyMapper.findByCode("USD")).thenReturn(sampleCurrency(1L, "USD", "US Dollar", "$", 2));

        assertThatThrownBy(() -> currencyService.create(request))
                .isInstanceOf(CurrencyCodeConflictException.class);

        verify(currencyMapper, never()).insert(any());
    }

    @Test
    void createInsertsAndReturnsCreatedCurrency() {
        CurrencyCreateRequest request = new CurrencyCreateRequest("USD", "US Dollar", "$", 2);
        when(currencyMapper.findByCode("USD")).thenReturn(null);
        doAnswer(invocation -> {
            Currency currency = invocation.getArgument(0);
            currency.setId(1L);
            return 1;
        }).when(currencyMapper).insert(any(Currency.class));
        when(currencyMapper.findById(1L)).thenReturn(sampleCurrency(1L, "USD", "US Dollar", "$", 2));

        CurrencyResponse response = currencyService.create(request);

        verify(currencyMapper, times(1)).insert(any(Currency.class));
        assertThat(response.getCode()).isEqualTo("USD");
    }

    @Test
    void updateThrowsNotFoundWhenCurrencyMissing() {
        when(currencyMapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> currencyService.update(999L, new CurrencyUpdateRequest("Name", "$", 2)))
                .isInstanceOf(CurrencyNotFoundException.class);
    }

    @Test
    void updateRejectsInvalidDecimalPlaces() {
        when(currencyMapper.findById(1L)).thenReturn(sampleCurrency(1L, "USD", "US Dollar", "$", 2));

        assertThatThrownBy(() -> currencyService.update(1L, new CurrencyUpdateRequest("Name", "$", -1)))
                .isInstanceOf(InvalidRequestException.class);

        verify(currencyMapper, never()).update(anyLong(), any(), any(), any());
    }

    @Test
    void updateAppliesNameSymbolDecimalPlacesAndLeavesCodeUnchanged() {
        when(currencyMapper.findById(1L))
                .thenReturn(sampleCurrency(1L, "USD", "US Dollar", "$", 2))
                .thenReturn(sampleCurrency(1L, "USD", "US Dollar (updated)", "US$", 4));

        CurrencyResponse response = currencyService.update(1L, new CurrencyUpdateRequest("US Dollar (updated)", "US$", 4));

        verify(currencyMapper, times(1)).update(eq(1L), eq("US Dollar (updated)"), eq("US$"), eq(4));
        assertThat(response.getCode()).isEqualTo("USD");
        assertThat(response.getName()).isEqualTo("US Dollar (updated)");
        assertThat(response.getSymbol()).isEqualTo("US$");
        assertThat(response.getDecimalPlaces()).isEqualTo(4);
    }

    @Test
    void deleteThrowsNotFoundWhenCurrencyMissing() {
        when(currencyMapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> currencyService.delete(999L))
                .isInstanceOf(CurrencyNotFoundException.class);

        verify(currencyMapper, never()).deleteById(anyLong());
    }

    @Test
    void deleteRemovesExistingCurrency() {
        when(currencyMapper.findById(1L)).thenReturn(sampleCurrency(1L, "USD", "US Dollar", "$", 2));

        currencyService.delete(1L);

        verify(currencyMapper, times(1)).deleteById(1L);
    }
}
