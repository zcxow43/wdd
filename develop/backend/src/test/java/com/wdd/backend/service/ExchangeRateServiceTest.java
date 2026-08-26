package com.wdd.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.wdd.backend.dto.Brand;
import com.wdd.backend.dto.CurrencyPairDefinition;
import com.wdd.backend.dto.ExchangeRate;
import com.wdd.backend.dto.ExchangeRateEffectiveSpread;
import com.wdd.backend.dto.ExchangeRateSyncResponse;
import com.wdd.backend.exception.ExchangeRateProviderUnavailableException;
import com.wdd.backend.exception.ExchangeRateSyncCooldownException;
import com.wdd.backend.exception.InvalidRequestException;
import com.wdd.backend.mapper.BrandMapper;
import com.wdd.backend.mapper.CurrencyPairDefinitionMapper;
import com.wdd.backend.mapper.ExchangeRateMapper;

class ExchangeRateServiceTest {

    private ExchangeRateMapper exchangeRateMapper;
    private CurrencyPairDefinitionMapper currencyPairDefinitionMapper;
    private BrandMapper brandMapper;
    private RestTemplate restTemplate;
    private MockRestServiceServer mockServer;
    private ExchangeRateService service;

    @BeforeEach
    void setUp() {
        exchangeRateMapper = mock(ExchangeRateMapper.class);
        currencyPairDefinitionMapper = mock(CurrencyPairDefinitionMapper.class);
        brandMapper = mock(BrandMapper.class);
        restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.bindTo(restTemplate).build();
        service = new ExchangeRateService(exchangeRateMapper, currencyPairDefinitionMapper, brandMapper, restTemplate);

        // No cooldown and no configured spreads unless a test overrides it.
        when(exchangeRateMapper.findSecondsSinceLastUpdate()).thenReturn(null);
        when(exchangeRateMapper.findEffectiveSpreadsForAllBrands()).thenReturn(List.of());
    }

    private static CurrencyPairDefinition definition(Long id, String baseCode, String quoteCode) {
        CurrencyPairDefinition definition = new CurrencyPairDefinition();
        definition.setId(id);
        definition.setBaseCurrencyCode(baseCode);
        definition.setQuoteCurrencyCode(quoteCode);
        definition.setPrecision(4);
        return definition;
    }

    private static ExchangeRateEffectiveSpread spread(Long definitionId, Long brandId, String brandCode,
            String depositSpreadPercent, String withdrawalSpreadPercent) {
        ExchangeRateEffectiveSpread spread = new ExchangeRateEffectiveSpread();
        spread.setCurrencyPairDefinitionId(definitionId);
        spread.setBrandId(brandId);
        spread.setBrandCode(brandCode);
        spread.setDepositSpreadPercent(new BigDecimal(depositSpreadPercent));
        spread.setWithdrawalSpreadPercent(new BigDecimal(withdrawalSpreadPercent));
        return spread;
    }

    private static Brand brand(Long id) {
        Brand brand = new Brand();
        brand.setId(id);
        brand.setCode("brand" + id);
        return brand;
    }

    // ---- GET /latest ----

    @Test
    void findLatestThrowsInvalidRequestForUnknownBrandId() {
        when(brandMapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.findLatest(999L)).isInstanceOf(InvalidRequestException.class);
        verify(exchangeRateMapper, never()).findLatestPerDefinitionAndBrand(any());
    }

    @Test
    void findLatestPassesKnownBrandIdThrough() {
        when(brandMapper.findById(1L)).thenReturn(brand(1L));
        when(exchangeRateMapper.findLatestPerDefinitionAndBrand(1L)).thenReturn(List.of());

        service.findLatest(1L);

        verify(exchangeRateMapper).findLatestPerDefinitionAndBrand(1L);
    }

    @Test
    void findLatestAllowsNullBrandId() {
        when(exchangeRateMapper.findLatestPerDefinitionAndBrand(null)).thenReturn(List.of());

        service.findLatest(null);

        verify(exchangeRateMapper).findLatestPerDefinitionAndBrand(null);
        verify(brandMapper, never()).findById(any());
    }

    // ---- POST /sync ----

    @Test
    void syncRejectsCooldownBeforeAnyExternalCallOrDataLoad() {
        when(exchangeRateMapper.findSecondsSinceLastUpdate()).thenReturn(23L);

        assertThatThrownBy(service::sync)
                .isInstanceOf(ExchangeRateSyncCooldownException.class)
                .satisfies(ex -> assertThat(((ExchangeRateSyncCooldownException) ex).getRetryAfterSeconds())
                        .isEqualTo(37L));
        verify(exchangeRateMapper, never()).upsert(any());
        verify(currencyPairDefinitionMapper, never()).findAll();
        verify(exchangeRateMapper, never()).findEffectiveSpreadsForAllBrands();
    }

    @Test
    void syncCallsProviderOncePerDistinctBaseCurrencyNotPerDefinitionOrBrand() {
        when(currencyPairDefinitionMapper.findAll()).thenReturn(List.of(
                definition(1L, "USD", "JPY"),
                definition(2L, "USD", "TWD"),
                definition(3L, "USD", "EUR")));
        when(exchangeRateMapper.findEffectiveSpreadsForAllBrands()).thenReturn(List.of(
                spread(1L, 1L, "au", "0.05", "0.10"),
                spread(2L, 1L, "au", "0.05", "0.10"),
                spread(3L, 1L, "au", "0.05", "0.10")));
        mockServer.expect(requestTo("https://open.er-api.com/v6/latest/USD"))
                .andRespond(withSuccess(
                        "{\"result\":\"success\",\"base_code\":\"USD\",\"rates\":{\"JPY\":150.00,\"TWD\":31.00,\"EUR\":0.90}}",
                        MediaType.APPLICATION_JSON));

        ExchangeRateSyncResponse response = service.sync();

        assertThat(response.getUpdated()).hasSize(3);
        mockServer.verify();
        verify(exchangeRateMapper, times(3)).upsert(any(ExchangeRate.class));
    }

    @Test
    void syncFansOutAcrossEveryBrandForOneDefinition() {
        when(currencyPairDefinitionMapper.findAll()).thenReturn(List.of(definition(1L, "USD", "JPY")));
        when(exchangeRateMapper.findEffectiveSpreadsForAllBrands()).thenReturn(List.of(
                spread(1L, 1L, "au", "0.05", "0.10"),
                spread(1L, 2L, "eu", "0.02", "0.03")));
        mockServer.expect(requestTo("https://open.er-api.com/v6/latest/USD"))
                .andRespond(withSuccess(
                        "{\"result\":\"success\",\"base_code\":\"USD\",\"rates\":{\"JPY\":149.85}}",
                        MediaType.APPLICATION_JSON));

        ExchangeRateSyncResponse response = service.sync();

        assertThat(response.getUpdated()).hasSize(2);
        verify(exchangeRateMapper, times(2)).upsert(any(ExchangeRate.class));

        var au = response.getUpdated().stream().filter(u -> u.getBrandId().equals(1L)).findFirst().orElseThrow();
        var eu = response.getUpdated().stream().filter(u -> u.getBrandId().equals(2L)).findFirst().orElseThrow();

        assertThat(au.getRate()).isEqualByComparingTo(eu.getRate());
        assertThat(au.getDepositRate()).isEqualByComparingTo(new BigDecimal("149.924925"));
        assertThat(au.getWithdrawalRate()).isEqualByComparingTo(new BigDecimal("149.99985"));
        assertThat(eu.getDepositRate()).isEqualByComparingTo(new BigDecimal("149.87997"));
        assertThat(eu.getWithdrawalRate()).isEqualByComparingTo(new BigDecimal("149.894955"));
        assertThat(au.getDepositRate()).isNotEqualByComparingTo(eu.getDepositRate());
        assertThat(au.getWithdrawalRate()).isNotEqualByComparingTo(eu.getWithdrawalRate());
    }

    @Test
    void syncComputesDepositAndWithdrawalRateAsRateMultipliedByOnePlusSpreadPercent() {
        when(currencyPairDefinitionMapper.findAll()).thenReturn(List.of(definition(1L, "USD", "JPY")));
        when(exchangeRateMapper.findEffectiveSpreadsForAllBrands()).thenReturn(List.of(
                spread(1L, 1L, "au", "0.05", "0.25")));
        mockServer.expect(requestTo("https://open.er-api.com/v6/latest/USD"))
                .andRespond(withSuccess(
                        "{\"result\":\"success\",\"base_code\":\"USD\",\"rates\":{\"JPY\":149.85}}",
                        MediaType.APPLICATION_JSON));

        ExchangeRateSyncResponse response = service.sync();

        var item = response.getUpdated().get(0);
        assertThat(item.getRate()).isEqualByComparingTo(new BigDecimal("149.85"));
        assertThat(item.getDepositRate()).isEqualByComparingTo(new BigDecimal("149.924925"));
        assertThat(item.getWithdrawalRate()).isEqualByComparingTo(new BigDecimal("150.224625"));
    }

    @Test
    void syncSkipsQuoteCodeAbsentFromProviderResponseOncePerDefinitionRegardlessOfBrandCount() {
        when(currencyPairDefinitionMapper.findAll()).thenReturn(List.of(
                definition(1L, "USD", "JPY"),
                definition(2L, "USD", "XXX")));
        when(exchangeRateMapper.findEffectiveSpreadsForAllBrands()).thenReturn(List.of(
                spread(1L, 1L, "au", "0.05", "0.10"),
                spread(2L, 1L, "au", "0.05", "0.10"),
                spread(2L, 2L, "eu", "0.02", "0.03")));
        mockServer.expect(requestTo("https://open.er-api.com/v6/latest/USD"))
                .andRespond(withSuccess(
                        "{\"result\":\"success\",\"base_code\":\"USD\",\"rates\":{\"JPY\":150.00}}",
                        MediaType.APPLICATION_JSON));

        ExchangeRateSyncResponse response = service.sync();

        assertThat(response.getUpdated()).hasSize(1);
        assertThat(response.getSkipped()).hasSize(1);
        assertThat(response.getSkipped().get(0).getCurrencyPairDefinitionId()).isEqualTo(2L);
        assertThat(response.getSkipped().get(0).getReason()).isEqualTo("not returned by provider");
    }

    @Test
    void syncContinuesOtherBaseCurrenciesWhenOneGroupFails() {
        when(currencyPairDefinitionMapper.findAll()).thenReturn(List.of(
                definition(1L, "USD", "JPY"),
                definition(2L, "EUR", "USD")));
        when(exchangeRateMapper.findEffectiveSpreadsForAllBrands()).thenReturn(List.of(
                spread(1L, 1L, "au", "0.05", "0.10"),
                spread(2L, 1L, "au", "0.05", "0.10")));
        mockServer.expect(requestTo("https://open.er-api.com/v6/latest/USD"))
                .andRespond(withSuccess(
                        "{\"result\":\"success\",\"base_code\":\"USD\",\"rates\":{\"JPY\":150.00}}",
                        MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo("https://open.er-api.com/v6/latest/EUR"))
                .andRespond(withServerError());

        ExchangeRateSyncResponse response = service.sync();

        assertThat(response.getUpdated()).hasSize(1);
        assertThat(response.getUpdated().get(0).getCurrencyPairDefinitionId()).isEqualTo(1L);
        assertThat(response.getSkipped()).isEmpty();
        verify(exchangeRateMapper, times(1)).upsert(any(ExchangeRate.class));
    }

    @Test
    void syncReturns502AndWritesNothingWhenEveryBaseCurrencyFails() {
        when(currencyPairDefinitionMapper.findAll()).thenReturn(List.of(
                definition(1L, "USD", "JPY"),
                definition(2L, "EUR", "USD")));
        when(exchangeRateMapper.findEffectiveSpreadsForAllBrands()).thenReturn(List.of(
                spread(1L, 1L, "au", "0.05", "0.10"),
                spread(2L, 1L, "au", "0.05", "0.10")));
        mockServer.expect(requestTo("https://open.er-api.com/v6/latest/USD"))
                .andRespond(withServerError());
        mockServer.expect(requestTo("https://open.er-api.com/v6/latest/EUR"))
                .andRespond(withServerError());

        assertThatThrownBy(service::sync).isInstanceOf(ExchangeRateProviderUnavailableException.class);
        verify(exchangeRateMapper, never()).upsert(any());
    }

    @Test
    void syncTreatsNonSuccessResultAsFailure() {
        when(currencyPairDefinitionMapper.findAll()).thenReturn(List.of(definition(1L, "ZZZ", "USD")));
        when(exchangeRateMapper.findEffectiveSpreadsForAllBrands()).thenReturn(List.of(
                spread(1L, 1L, "au", "0.05", "0.10")));
        mockServer.expect(requestTo("https://open.er-api.com/v6/latest/ZZZ"))
                .andRespond(withSuccess(
                        "{\"result\":\"error\",\"error-type\":\"unsupported-code\"}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(service::sync).isInstanceOf(ExchangeRateProviderUnavailableException.class);
        verify(exchangeRateMapper, never()).upsert(any());
    }

    @Test
    void syncWritesNothingForADefinitionWithNoConfiguredBrandsButDoesNotFailOrSkip() {
        when(currencyPairDefinitionMapper.findAll()).thenReturn(List.of(definition(1L, "USD", "JPY")));
        when(exchangeRateMapper.findEffectiveSpreadsForAllBrands()).thenReturn(List.of());
        mockServer.expect(requestTo("https://open.er-api.com/v6/latest/USD"))
                .andRespond(withSuccess(
                        "{\"result\":\"success\",\"base_code\":\"USD\",\"rates\":{\"JPY\":150.00}}",
                        MediaType.APPLICATION_JSON));

        ExchangeRateSyncResponse response = service.sync();

        assertThat(response.getUpdated()).isEmpty();
        assertThat(response.getSkipped()).isEmpty();
        verify(exchangeRateMapper, never()).upsert(any());
    }
}
