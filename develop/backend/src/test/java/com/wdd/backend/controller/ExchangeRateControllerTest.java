package com.wdd.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Exercises the parts of the sync/latest endpoints that do not require an
 * actual external network call to open.er-api.com: {@code GET /latest}'s
 * brand fan-out shape, its {@code brandId} filter/validation (400 for an
 * unknown brand), and the sync's cooldown short-circuit (429), which per
 * spec runs before any external call is made and before any brand/spread
 * data is even loaded. Full round-trip sync success against the live
 * provider (brand fan-out writes, per-brand deposit/withdrawal spread
 * resolution) is verified manually per the spec's execution result, not
 * re-run on every build.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ExchangeRateControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final TestRestTemplate restTemplate = new TestRestTemplate();

    private final List<Long> createdCurrencyIds = new ArrayList<>();
    private final List<Long> createdDefinitionIds = new ArrayList<>();

    private String latestUrl() {
        return "http://localhost:" + port + "/api/exchange-rates/latest";
    }

    private String syncUrl() {
        return "http://localhost:" + port + "/api/exchange-rates/sync";
    }

    private String currenciesUrl() {
        return "http://localhost:" + port + "/api/currencies";
    }

    private String definitionsUrl() {
        return "http://localhost:" + port + "/api/currency-pair-definitions";
    }

    private HttpEntity<String> jsonEntity(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private Long createCurrency(String code) {
        String body = String.format(
                "{\"code\": \"%s\", \"name\": \"%s\", \"symbol\": \"%s\", \"decimalPlaces\": 2}",
                code, code, code);
        ResponseEntity<Map> response = restTemplate.postForEntity(currenciesUrl(), jsonEntity(body), Map.class);
        Long id = ((Number) response.getBody().get("id")).longValue();
        createdCurrencyIds.add(id);
        return id;
    }

    private Long createDefinition(Long baseCurrencyId, Long quoteCurrencyId, Integer precision) {
        String body = String.format(
                "{\"baseCurrencyId\": %d, \"quoteCurrencyId\": %d, \"precision\": %d}",
                baseCurrencyId, quoteCurrencyId, precision);
        ResponseEntity<Map> response = restTemplate.postForEntity(definitionsUrl(), jsonEntity(body), Map.class);
        Long id = ((Number) response.getBody().get("id")).longValue();
        createdDefinitionIds.add(id);
        return id;
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM exchange_rate WHERE source = 'test-seed'");
        for (Long id : createdDefinitionIds) {
            // ON DELETE CASCADE removes any fanned-out currency_pair rows and synced exchange_rate rows.
            restTemplate.delete(definitionsUrl() + "/" + id);
        }
        createdDefinitionIds.clear();
        for (Long id : createdCurrencyIds) {
            restTemplate.delete(currenciesUrl() + "/" + id);
        }
        createdCurrencyIds.clear();
    }

    @Test
    void latestReturnsOneEntryPerDefinitionAndBrandCombinationWithNullFieldsWhenNeverSynced() {
        Long baseId = createCurrency("XLA");
        Long quoteId = createCurrency("XLB");
        createDefinition(baseId, quoteId, 2);

        long brandCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM brand", Long.class);

        ResponseEntity<Map[]> response = restTemplate.getForEntity(latestUrl(), Map[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map> body = List.of(response.getBody());
        assertThat(body).isNotEmpty();
        assertThat(body).allSatisfy(entry -> assertThat(entry).containsKeys("currencyPairDefinitionId",
                "baseCurrencyCode", "quoteCurrencyCode", "precision", "brandId", "brandCode", "rate", "depositRate",
                "withdrawalRate", "rateMinute", "source"));

        List<Map> newlyCreated = body.stream()
                .filter(entry -> ((Number) entry.get("currencyPairDefinitionId")).longValue()
                        == createdDefinitionIds.get(0))
                .toList();
        assertThat(newlyCreated).hasSize((int) brandCount);
        assertThat(newlyCreated).allSatisfy(entry -> {
            assertThat(entry.get("rate")).isNull();
            assertThat(entry.get("depositRate")).isNull();
            assertThat(entry.get("withdrawalRate")).isNull();
            assertThat(entry.get("rateMinute")).isNull();
            assertThat(entry.get("source")).isNull();
        });
    }

    @Test
    void latestWithKnownBrandIdScopesToJustThatBrand() {
        Long baseId = createCurrency("XLC");
        Long quoteId = createCurrency("XLD");
        createDefinition(baseId, quoteId, 2);
        Long brandId = jdbcTemplate.queryForObject("SELECT id FROM brand ORDER BY id LIMIT 1", Long.class);

        ResponseEntity<Map[]> response = restTemplate.getForEntity(latestUrl() + "?brandId=" + brandId, Map[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map> body = List.of(response.getBody());
        assertThat(body).allSatisfy(entry -> assertThat(((Number) entry.get("brandId")).longValue())
                .isEqualTo(brandId));
        List<Map> forNewDefinition = body.stream()
                .filter(entry -> ((Number) entry.get("currencyPairDefinitionId")).longValue()
                        == createdDefinitionIds.get(0))
                .toList();
        assertThat(forNewDefinition).hasSize(1);
    }

    @Test
    void latestWithUnknownBrandIdReturnsBadRequest() {
        ResponseEntity<String> response = restTemplate.getForEntity(latestUrl() + "?brandId=999999999", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void syncWithinCooldownReturnsTooManyRequestsWithoutExternalCallOrWrite() {
        // Seed a row whose updated_at is effectively "now" (whole-second
        // precision, matching the TIMESTAMPDIFF-in-SQL cooldown check) so the
        // cooldown is guaranteed active regardless of any prior test/manual
        // sync timing.
        Long definitionId = jdbcTemplate.queryForObject(
                "SELECT id FROM currency_pair_definition ORDER BY id LIMIT 1", Long.class);
        Long brandId = jdbcTemplate.queryForObject("SELECT id FROM brand ORDER BY id LIMIT 1", Long.class);
        jdbcTemplate.update(
                "INSERT INTO exchange_rate (currency_pair_definition_id, brand_id, rate, deposit_rate, "
                        + "withdrawal_rate, rate_minute, source, updated_at) VALUES (?, ?, 1, 1, 1, NOW(), "
                        + "'test-seed', NOW())",
                definitionId, brandId);

        ResponseEntity<Map> response = restTemplate.postForEntity(syncUrl(), null, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody()).containsKey("retryAfterSeconds");
        Number retryAfterSeconds = (Number) response.getBody().get("retryAfterSeconds");
        assertThat(retryAfterSeconds.longValue()).isBetween(1L, 60L);
    }
}
