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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CurrencyPairControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final TestRestTemplate restTemplate = new TestRestTemplate();

    private final List<Long> createdCurrencyPairIds = new ArrayList<>();
    private final List<Long> createdDefinitionIds = new ArrayList<>();
    private final List<Long> createdCurrencyIds = new ArrayList<>();

    private String currencyPairsUrl() {
        return "http://localhost:" + port + "/api/currency-pairs";
    }

    private String definitionsUrl() {
        return "http://localhost:" + port + "/api/currency-pair-definitions";
    }

    private String currenciesUrl() {
        return "http://localhost:" + port + "/api/currencies";
    }

    @AfterEach
    void cleanUp() {
        for (Long id : createdCurrencyPairIds) {
            restTemplate.delete(currencyPairsUrl() + "/" + id);
        }
        createdCurrencyPairIds.clear();
        for (Long id : createdDefinitionIds) {
            // ON DELETE CASCADE removes any remaining fanned-out currency_pair rows.
            restTemplate.delete(definitionsUrl() + "/" + id);
        }
        createdDefinitionIds.clear();
        for (Long id : createdCurrencyIds) {
            restTemplate.delete(currenciesUrl() + "/" + id);
        }
        createdCurrencyIds.clear();
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

    /** Deletes any fanned-out currency_pair rows for a definition so a fresh one can be POSTed directly. */
    private void deleteFannedOutPairs(Long definitionId) {
        jdbcTemplate.update("DELETE FROM currency_pair WHERE currency_pair_definition_id = ?", definitionId);
    }

    private Long firstBrandId() {
        return jdbcTemplate.queryForObject("SELECT id FROM brand ORDER BY id LIMIT 1", Long.class);
    }

    private Long secondBrandId() {
        return jdbcTemplate.queryForObject("SELECT id FROM brand ORDER BY id LIMIT 1 OFFSET 1", Long.class);
    }

    private ResponseEntity<Map> createCurrencyPair(Long definitionId, Long brandId, String rateType,
            String rate, Boolean active) {
        StringBuilder body = new StringBuilder("{");
        body.append("\"currencyPairDefinitionId\": ").append(definitionId).append(", ");
        body.append("\"brandId\": ").append(brandId);
        if (rateType != null) {
            body.append(", \"rateType\": \"").append(rateType).append("\"");
        }
        if (rate != null) {
            body.append(", \"rate\": ").append(rate);
        }
        if (active != null) {
            body.append(", \"active\": ").append(active);
        }
        body.append("}");
        ResponseEntity<Map> response = restTemplate.postForEntity(currencyPairsUrl(), jsonEntity(body.toString()),
                Map.class);
        if (response.getStatusCode() == HttpStatus.CREATED) {
            createdCurrencyPairIds.add(((Number) response.getBody().get("id")).longValue());
        }
        return response;
    }

    @Test
    void getCurrencyPairByIdReturnsNotFoundForUnknownId() {
        ResponseEntity<String> response = restTemplate.getForEntity(currencyPairsUrl() + "/999999", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void listCurrencyPairsIncludesFannedOutRowsAndFiltersByDefinitionIdBrandIdAndActive() {
        Long baseId = createCurrency("QPA");
        Long quoteId = createCurrency("QPB");
        Long definitionId = createDefinition(baseId, quoteId, 4);

        ResponseEntity<Map[]> allForDefinition = restTemplate.getForEntity(
                currencyPairsUrl() + "?currencyPairDefinitionId=" + definitionId, Map[].class);
        assertThat(allForDefinition.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(allForDefinition.getBody()).hasSize(7);

        Long brandId = firstBrandId();
        ResponseEntity<Map[]> byBrand = restTemplate.getForEntity(
                currencyPairsUrl() + "?currencyPairDefinitionId=" + definitionId + "&brandId=" + brandId,
                Map[].class);
        assertThat(byBrand.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(byBrand.getBody()).hasSize(1);
        assertThat(byBrand.getBody()[0].get("brandId")).isEqualTo(brandId.intValue());
        assertThat(byBrand.getBody()[0].get("baseCurrencyCode")).isEqualTo("QPA");
        assertThat(byBrand.getBody()[0].get("quoteCurrencyCode")).isEqualTo("QPB");

        ResponseEntity<Map[]> byActive = restTemplate.getForEntity(
                currencyPairsUrl() + "?currencyPairDefinitionId=" + definitionId + "&active=true", Map[].class);
        assertThat(byActive.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(byActive.getBody()).isEmpty();
    }

    @Test
    void createWithAutoRateTypeCreatesRowWithNullRateEvenIfRateSent() {
        Long baseId = createCurrency("QPC");
        Long quoteId = createCurrency("QPD");
        Long definitionId = createDefinition(baseId, quoteId, 4);
        deleteFannedOutPairs(definitionId);
        Long brandId = firstBrandId();

        ResponseEntity<Map> response = createCurrencyPair(definitionId, brandId, "AUTO", "150.25", false);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("rateType")).isEqualTo("AUTO");
        assertThat(response.getBody().get("rate")).isNull();
        assertThat(response.getBody().get("baseCurrencyCode")).isEqualTo("QPC");
        assertThat(response.getBody().get("quoteCurrencyCode")).isEqualTo("QPD");
    }

    @Test
    void createWithManualRateTypeAndNoRateReturnsBadRequest() {
        Long baseId = createCurrency("QPE");
        Long quoteId = createCurrency("QPF");
        Long definitionId = createDefinition(baseId, quoteId, 4);
        deleteFannedOutPairs(definitionId);
        Long brandId = firstBrandId();

        ResponseEntity<Map> response = createCurrencyPair(definitionId, brandId, "MANUAL", null, false);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createWithManualRateTypeAndNonPositiveRateReturnsBadRequest() {
        Long baseId = createCurrency("QPG");
        Long quoteId = createCurrency("QPH");
        Long definitionId = createDefinition(baseId, quoteId, 4);
        deleteFannedOutPairs(definitionId);
        Long brandId = firstBrandId();

        ResponseEntity<Map> response = createCurrencyPair(definitionId, brandId, "MANUAL", "0", false);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createWithManualRateExceedingPrecisionReturnsBadRequest() {
        Long baseId = createCurrency("QPI");
        Long quoteId = createCurrency("QPJ");
        Long definitionId = createDefinition(baseId, quoteId, 2);
        deleteFannedOutPairs(definitionId);
        Long brandId = firstBrandId();

        ResponseEntity<Map> response = createCurrencyPair(definitionId, brandId, "MANUAL", "150.255", false);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createWithDuplicateDefinitionAndBrandReturnsConflict() {
        Long baseId = createCurrency("QPK");
        Long quoteId = createCurrency("QPL");
        Long definitionId = createDefinition(baseId, quoteId, 4);
        Long brandId = firstBrandId();

        // A currency_pair row for this (definition, brand) already exists via fan-out.
        ResponseEntity<Map> response = createCurrencyPair(definitionId, brandId, "AUTO", null, false);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void createWithNonExistentDefinitionOrBrandReturnsBadRequest() {
        Long brandId = firstBrandId();

        ResponseEntity<Map> badDefinition = createCurrencyPair(999999L, brandId, "AUTO", null, false);
        assertThat(badDefinition.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        Long baseId = createCurrency("QPM");
        Long quoteId = createCurrency("QPN");
        Long definitionId = createDefinition(baseId, quoteId, 4);
        deleteFannedOutPairs(definitionId);

        ResponseEntity<Map> badBrand = createCurrencyPair(definitionId, 999999L, "AUTO", null, false);
        assertThat(badBrand.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateCanToggleActiveIndependentlyOfRateTypeAndRate() {
        Long baseId = createCurrency("QPO");
        Long quoteId = createCurrency("QPP");
        Long definitionId = createDefinition(baseId, quoteId, 4);
        deleteFannedOutPairs(definitionId);
        Long brandId = firstBrandId();
        ResponseEntity<Map> created = createCurrencyPair(definitionId, brandId, "MANUAL", "150.25", false);
        Long id = ((Number) created.getBody().get("id")).longValue();

        ResponseEntity<Map> response = restTemplate.exchange(currencyPairsUrl() + "/" + id, HttpMethod.PUT,
                jsonEntity("{\"active\": true}"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("active")).isEqualTo(Boolean.TRUE);
        assertThat(response.getBody().get("rateType")).isEqualTo("MANUAL");
        assertThat(response.getBody().get("rate")).isEqualTo(150.25);
    }

    @Test
    void updateSwitchingRateTypeFromManualToAutoClearsRate() {
        Long baseId = createCurrency("QPQ");
        Long quoteId = createCurrency("QPR");
        Long definitionId = createDefinition(baseId, quoteId, 4);
        deleteFannedOutPairs(definitionId);
        Long brandId = firstBrandId();
        ResponseEntity<Map> created = createCurrencyPair(definitionId, brandId, "MANUAL", "150.25", false);
        Long id = ((Number) created.getBody().get("id")).longValue();

        ResponseEntity<Map> response = restTemplate.exchange(currencyPairsUrl() + "/" + id, HttpMethod.PUT,
                jsonEntity("{\"rateType\": \"AUTO\"}"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("rateType")).isEqualTo("AUTO");
        assertThat(response.getBody().get("rate")).isNull();
    }

    @Test
    void updateAndDeleteForUnknownIdReturnNotFound() {
        ResponseEntity<String> updateResponse = restTemplate.exchange(currencyPairsUrl() + "/999999",
                HttpMethod.PUT, jsonEntity("{\"active\": true}"), String.class);
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<String> deleteResponse = restTemplate.exchange(currencyPairsUrl() + "/999999",
                HttpMethod.DELETE, null, String.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteSucceedsRegardlessOfActiveState() {
        Long baseId = createCurrency("QPS");
        Long quoteId = createCurrency("QPT");
        Long definitionId = createDefinition(baseId, quoteId, 4);
        deleteFannedOutPairs(definitionId);
        Long brandId = firstBrandId();
        ResponseEntity<Map> created = createCurrencyPair(definitionId, brandId, "AUTO", null, true);
        Long id = ((Number) created.getBody().get("id")).longValue();
        createdCurrencyPairIds.remove(id);

        assertThat(created.getBody().get("active")).isEqualTo(Boolean.TRUE);

        ResponseEntity<Void> response = restTemplate.exchange(currencyPairsUrl() + "/" + id, HttpMethod.DELETE,
                null, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> getResponse = restTemplate.getForEntity(currencyPairsUrl() + "/" + id, String.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createAndDeleteIndividualRowAfterFanOutRowDeleted() {
        Long baseId = createCurrency("QPU");
        Long quoteId = createCurrency("QPV");
        Long definitionId = createDefinition(baseId, quoteId, 4);
        Long brandId = secondBrandId();

        // Simulate the fanned-out row for this brand having been deleted, then recreated directly.
        jdbcTemplate.update(
                "DELETE FROM currency_pair WHERE currency_pair_definition_id = ? AND brand_id = ?",
                definitionId, brandId);

        ResponseEntity<Map> response = createCurrencyPair(definitionId, brandId, "AUTO", null, false);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("brandId")).isEqualTo(brandId.intValue());
    }
}
