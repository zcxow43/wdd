package com.wdd.backend.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.wdd.backend.dto.CurrencyPairDefinition;
import com.wdd.backend.dto.ExchangeRate;
import com.wdd.backend.dto.ExchangeRateEffectiveSpread;
import com.wdd.backend.dto.ExchangeRateLatest;
import com.wdd.backend.dto.ExchangeRateLatestResponse;
import com.wdd.backend.dto.ExchangeRateProviderResponse;
import com.wdd.backend.dto.ExchangeRateSyncResponse;
import com.wdd.backend.dto.ExchangeRateSyncSkippedItem;
import com.wdd.backend.dto.ExchangeRateSyncUpdatedItem;
import com.wdd.backend.exception.ExchangeRateProviderUnavailableException;
import com.wdd.backend.exception.ExchangeRateSyncCooldownException;
import com.wdd.backend.exception.InvalidRequestException;
import com.wdd.backend.mapper.BrandMapper;
import com.wdd.backend.mapper.CurrencyPairDefinitionMapper;
import com.wdd.backend.mapper.ExchangeRateMapper;

/**
 * Syncs market rates from the free, key-less open.er-api.com provider and
 * fans out across every existing brand, storing one snapshot per
 * (definition, brand) per minute: the plain provider rate plus each brand's
 * currently-effective deposit/withdrawal spread, frozen at sync time. Plain
 * market data — a system action, not a brand-facing configuration change —
 * so writes here bypass the audit/approval flow entirely.
 */
@Service
public class ExchangeRateService {

    private static final int COOLDOWN_SECONDS = 60;
    private static final String SOURCE = "open.er-api.com";
    private static final String PROVIDER_URL_TEMPLATE = "https://open.er-api.com/v6/latest/{baseCode}";
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final ExchangeRateMapper exchangeRateMapper;
    private final CurrencyPairDefinitionMapper currencyPairDefinitionMapper;
    private final BrandMapper brandMapper;
    private final RestTemplate restTemplate;

    @Autowired
    public ExchangeRateService(ExchangeRateMapper exchangeRateMapper,
            CurrencyPairDefinitionMapper currencyPairDefinitionMapper, BrandMapper brandMapper,
            RestTemplateBuilder restTemplateBuilder) {
        this(exchangeRateMapper, currencyPairDefinitionMapper, brandMapper, restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(5))
                .build());
    }

    /**
     * Test seam: lets unit tests bind a {@link RestTemplate} to
     * {@code MockRestServiceServer} instead of the real
     * {@code RestTemplateBuilder}-constructed client, so the provider call
     * can be verified without touching the real network.
     */
    ExchangeRateService(ExchangeRateMapper exchangeRateMapper,
            CurrencyPairDefinitionMapper currencyPairDefinitionMapper, BrandMapper brandMapper,
            RestTemplate restTemplate) {
        this.exchangeRateMapper = exchangeRateMapper;
        this.currencyPairDefinitionMapper = currencyPairDefinitionMapper;
        this.brandMapper = brandMapper;
        this.restTemplate = restTemplate;
    }

    public List<ExchangeRateLatestResponse> findLatest(Long brandId) {
        if (brandId != null && brandMapper.findById(brandId) == null) {
            throw new InvalidRequestException("brandId does not reference an existing brand");
        }
        return exchangeRateMapper.findLatestPerDefinitionAndBrand(brandId).stream()
                .map(ExchangeRateService::toLatestResponse)
                .toList();
    }

    @Transactional
    public ExchangeRateSyncResponse sync() {
        checkCooldown();

        List<CurrencyPairDefinition> definitions = currencyPairDefinitionMapper.findAll();
        Map<String, List<CurrencyPairDefinition>> definitionsByBase = definitions.stream()
                .collect(Collectors.groupingBy(CurrencyPairDefinition::getBaseCurrencyCode, LinkedHashMap::new,
                        Collectors.toList()));

        Map<Long, List<ExchangeRateEffectiveSpread>> spreadsByDefinition =
                exchangeRateMapper.findEffectiveSpreadsForAllBrands().stream()
                        .collect(Collectors.groupingBy(ExchangeRateEffectiveSpread::getCurrencyPairDefinitionId));

        LocalDateTime rateMinute = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);

        List<ExchangeRateSyncUpdatedItem> updated = new ArrayList<>();
        List<ExchangeRateSyncSkippedItem> skipped = new ArrayList<>();
        boolean anyGroupFailed = false;

        for (Map.Entry<String, List<CurrencyPairDefinition>> entry : definitionsByBase.entrySet()) {
            String baseCode = entry.getKey();
            Map<String, BigDecimal> rates = fetchRates(baseCode);
            if (rates == null) {
                anyGroupFailed = true;
                continue;
            }

            for (CurrencyPairDefinition definition : entry.getValue()) {
                String quoteCode = definition.getQuoteCurrencyCode();
                BigDecimal rate = rates.get(quoteCode);
                if (rate == null) {
                    skipped.add(new ExchangeRateSyncSkippedItem(definition.getId(), baseCode, quoteCode,
                            "not returned by provider"));
                    continue;
                }

                List<ExchangeRateEffectiveSpread> spreadsForDefinition =
                        spreadsByDefinition.getOrDefault(definition.getId(), List.of());

                for (ExchangeRateEffectiveSpread spread : spreadsForDefinition) {
                    BigDecimal depositSpreadPercent = spread.getDepositSpreadPercent() != null
                            ? spread.getDepositSpreadPercent() : BigDecimal.ZERO;
                    BigDecimal withdrawalSpreadPercent = spread.getWithdrawalSpreadPercent() != null
                            ? spread.getWithdrawalSpreadPercent() : BigDecimal.ZERO;
                    BigDecimal depositRate = rate.multiply(
                            BigDecimal.ONE.add(depositSpreadPercent.divide(ONE_HUNDRED)));
                    BigDecimal withdrawalRate = rate.multiply(
                            BigDecimal.ONE.add(withdrawalSpreadPercent.divide(ONE_HUNDRED)));

                    ExchangeRate exchangeRate = new ExchangeRate();
                    exchangeRate.setCurrencyPairDefinitionId(definition.getId());
                    exchangeRate.setBrandId(spread.getBrandId());
                    exchangeRate.setRate(rate);
                    exchangeRate.setDepositRate(depositRate);
                    exchangeRate.setWithdrawalRate(withdrawalRate);
                    exchangeRate.setRateMinute(rateMinute);
                    exchangeRate.setSource(SOURCE);
                    exchangeRateMapper.upsert(exchangeRate);

                    updated.add(new ExchangeRateSyncUpdatedItem(definition.getId(), baseCode, quoteCode,
                            spread.getBrandId(), spread.getBrandCode(), rate, depositRate, withdrawalRate));
                }
            }
        }

        if (updated.isEmpty() && anyGroupFailed) {
            throw new ExchangeRateProviderUnavailableException();
        }

        return new ExchangeRateSyncResponse(rateMinute, updated, skipped);
    }

    private void checkCooldown() {
        Long secondsSinceLastUpdate = exchangeRateMapper.findSecondsSinceLastUpdate();
        if (secondsSinceLastUpdate == null) {
            return;
        }
        if (secondsSinceLastUpdate < COOLDOWN_SECONDS) {
            long retryAfterSeconds = COOLDOWN_SECONDS - secondsSinceLastUpdate;
            throw new ExchangeRateSyncCooldownException(retryAfterSeconds);
        }
    }

    /**
     * Calls the provider once for {@code baseCode}. Returns {@code null} on
     * any failure (non-2xx, network error, or {@code result != "success"})
     * so the caller can skip that whole base-currency group.
     */
    private Map<String, BigDecimal> fetchRates(String baseCode) {
        try {
            ExchangeRateProviderResponse body = restTemplate.getForObject(PROVIDER_URL_TEMPLATE,
                    ExchangeRateProviderResponse.class, baseCode);
            if (body == null || !"success".equals(body.getResult()) || body.getRates() == null) {
                return null;
            }
            return body.getRates();
        } catch (RestClientException ex) {
            return null;
        }
    }

    private static ExchangeRateLatestResponse toLatestResponse(ExchangeRateLatest latest) {
        return new ExchangeRateLatestResponse(
                latest.getCurrencyPairDefinitionId(),
                latest.getBaseCurrencyCode(),
                latest.getQuoteCurrencyCode(),
                latest.getPrecision(),
                latest.getBrandId(),
                latest.getBrandCode(),
                latest.getRate(),
                latest.getDepositRate(),
                latest.getWithdrawalRate(),
                latest.getRateMinute(),
                latest.getSource());
    }
}
