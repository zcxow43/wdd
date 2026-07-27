package pl.piomin.services.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import pl.piomin.services.backend.dto.CurrencyCreateRequest;
import pl.piomin.services.backend.dto.CurrencyUpdateRequest;
import pl.piomin.services.backend.exception.CurrencyCodeExistsException;
import pl.piomin.services.backend.exception.CurrencyInUseException;
import pl.piomin.services.backend.exception.CurrencyNotFoundException;
import pl.piomin.services.backend.mapper.CurrencyMapper;
import pl.piomin.services.backend.mapper.CurrencyPairMapper;
import pl.piomin.services.backend.model.Currency;

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

    private Currency sampleCurrency(Long id, String code) {
        Currency currency = new Currency();
        currency.setId(id);
        currency.setCode(code);
        currency.setName("Sample Currency");
        currency.setDecimalPlaces(2);
        currency.setActive(true);
        return currency;
    }

    @Test
    void list_returnsAllCurrenciesFromMapper() {
        when(currencyMapper.findAll(null)).thenReturn(List.of(sampleCurrency(1L, "TWD")));

        List<Currency> result = currencyService.list(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCode()).isEqualTo("TWD");
    }

    @Test
    void list_filtersByActive() {
        when(currencyMapper.findAll(true)).thenReturn(List.of(sampleCurrency(1L, "TWD")));

        List<Currency> result = currencyService.list(true);

        assertThat(result).hasSize(1);
        verify(currencyMapper).findAll(true);
    }

    @Test
    void getById_returnsCurrency_whenFound() {
        when(currencyMapper.findById(1L)).thenReturn(sampleCurrency(1L, "TWD"));

        Currency result = currencyService.getById(1L);

        assertThat(result.getCode()).isEqualTo("TWD");
    }

    @Test
    void getById_throwsNotFound_whenMissing() {
        when(currencyMapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> currencyService.getById(999L))
                .isInstanceOf(CurrencyNotFoundException.class);
    }

    @Test
    void create_savesCurrency_whenCodeIsUnique() {
        CurrencyCreateRequest request = new CurrencyCreateRequest();
        request.setCode("KRW");
        request.setName("South Korean Won");
        request.setDecimalPlaces(0);

        when(currencyMapper.findByCode("KRW")).thenReturn(null);
        doAnswer(invocation -> {
            Currency toInsert = invocation.getArgument(0);
            toInsert.setId(1L);
            return 1;
        }).when(currencyMapper).insert(any(Currency.class));
        when(currencyMapper.findById(1L)).thenReturn(sampleCurrency(1L, "KRW"));

        Currency result = currencyService.create(request);

        assertThat(result.getCode()).isEqualTo("KRW");
        verify(currencyMapper).insert(any(Currency.class));
    }

    @Test
    void create_defaultsActiveToTrue_whenNotProvided() {
        CurrencyCreateRequest request = new CurrencyCreateRequest();
        request.setCode("KRW");
        request.setName("South Korean Won");
        request.setDecimalPlaces(0);

        when(currencyMapper.findByCode("KRW")).thenReturn(null);
        doAnswer(invocation -> {
            Currency toInsert = invocation.getArgument(0);
            toInsert.setId(1L);
            return 1;
        }).when(currencyMapper).insert(any(Currency.class));
        when(currencyMapper.findById(1L)).thenReturn(sampleCurrency(1L, "KRW"));

        currencyService.create(request);

        org.mockito.ArgumentCaptor<Currency> captor = org.mockito.ArgumentCaptor.forClass(Currency.class);
        verify(currencyMapper).insert(captor.capture());
        assertThat(captor.getValue().getActive()).isTrue();
    }

    @Test
    void create_throwsConflict_whenCodeAlreadyExists() {
        CurrencyCreateRequest request = new CurrencyCreateRequest();
        request.setCode("TWD");
        request.setName("New Taiwan Dollar");
        request.setDecimalPlaces(0);

        when(currencyMapper.findByCode("TWD")).thenReturn(sampleCurrency(1L, "TWD"));

        assertThatThrownBy(() -> currencyService.create(request))
                .isInstanceOf(CurrencyCodeExistsException.class);
        verify(currencyMapper, never()).insert(any(Currency.class));
    }

    @Test
    void update_appliesPartialChanges_whenFound() {
        Currency existing = sampleCurrency(1L, "TWD");
        when(currencyMapper.findById(1L)).thenReturn(existing).thenReturn(existing);

        CurrencyUpdateRequest request = new CurrencyUpdateRequest();
        request.setName("Updated Name");

        Currency result = currencyService.update(1L, request);

        assertThat(result.getName()).isEqualTo("Updated Name");
        verify(currencyMapper).update(any(Currency.class));
    }

    @Test
    void update_throwsNotFound_whenMissing() {
        when(currencyMapper.findById(999L)).thenReturn(null);

        CurrencyUpdateRequest request = new CurrencyUpdateRequest();
        request.setName("Updated Name");

        assertThatThrownBy(() -> currencyService.update(999L, request))
                .isInstanceOf(CurrencyNotFoundException.class);
        verify(currencyMapper, never()).update(any(Currency.class));
    }

    @Test
    void delete_removesCurrency_whenFound() {
        when(currencyMapper.findById(1L)).thenReturn(sampleCurrency(1L, "TWD"));
        when(currencyPairMapper.existsByCurrencyId(1L)).thenReturn(false);

        currencyService.delete(1L);

        verify(currencyMapper, times(1)).deleteById(1L);
    }

    @Test
    void delete_throwsNotFound_whenMissing() {
        when(currencyMapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> currencyService.delete(999L))
                .isInstanceOf(CurrencyNotFoundException.class);
        verify(currencyMapper, never()).deleteById(eq(999L));
    }

    @Test
    void delete_throwsConflict_whenReferencedByCurrencyPair() {
        when(currencyMapper.findById(1L)).thenReturn(sampleCurrency(1L, "TWD"));
        when(currencyPairMapper.existsByCurrencyId(1L)).thenReturn(true);

        assertThatThrownBy(() -> currencyService.delete(1L))
                .isInstanceOf(CurrencyInUseException.class);
        verify(currencyMapper, never()).deleteById(eq(1L));
    }
}
