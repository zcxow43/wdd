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
class CurrencyPairDefinitionControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final TestRestTemplate restTemplate = new TestRestTemplate();

    private final List<Long> createdDefinitionIds = new ArrayList<>();
    private final List<Long> createdCurrencyIds = new ArrayList<>();

    private String definitionsUrl() {
        return "http://localhost:" + port + "/api/currency-pair-definitions";
    }

    private String currenciesUrl() {
        return "http://localhost:" + port + "/api/currencies";
    }

    @AfterEach
    void cleanUp() {
        for (Long id : createdDefinitionIds) {
            // ON DELETE CASCADE removes the fanned-out currency_pair rows.
            restTemplate.delete(definitionsUrl() + "/" + id);
        }
        createdDefinitionIds.clear();
        for (Long id : createdCurrencyIds) {
            restTemplate.delete(currenciesUrl() + "/" + id);
        }
        createdCurrencyIds.clear();
    }

    private Long createCurrency(String code) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = String.format(
                "{\"code\": \"%s\", \"name\": \"%s\", \"symbol\": \"%s\", \"decimalPlaces\": 2}",
                code, code, code);
        ResponseEntity<Map> response = restTemplate.postForEntity(currenciesUrl(),
                new HttpEntity<>(body, headers), Map.class);
        Long id = ((Number) response.getBody().get("id")).longValue();
        createdCurrencyIds.add(id);
        return id;
    }

    private ResponseEntity<Map> createDefinition(Long baseCurrencyId, Long quoteCurrencyId, Integer precision) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String precisionField = precision == null ? "" : ", \"precision\": " + precision;
        String body = String.format(
                "{\"baseCurrencyId\": %d, \"quoteCurrencyId\": %d%s}",
                baseCurrencyId, quoteCurrencyId, precisionField);
        ResponseEntity<Map> response = restTemplate.postForEntity(definitionsUrl(),
                new HttpEntity<>(body, headers), Map.class);
        if (response.getStatusCode() == HttpStatus.CREATED) {
            createdDefinitionIds.add(((Number) response.getBody().get("id")).longValue());
        }
        return response;
    }

    private HttpEntity<String> jsonEntity(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    @Test
    void getDefinitionByIdReturnsNotFoundForUnknownId() {
        ResponseEntity<String> response = restTemplate.getForEntity(definitionsUrl() + "/999999", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createDefinitionFansOutOnePairPerBrandAndListsInResponse() {
        Long baseId = createCurrency("QDA");
        Long quoteId = createCurrency("QDB");

        ResponseEntity<Map> response = createDefinition(baseId, quoteId, 4);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("baseCurrencyId")).isEqualTo(baseId.intValue());
        assertThat(response.getBody().get("baseCurrencyCode")).isEqualTo("QDA");
        assertThat(response.getBody().get("quoteCurrencyId")).isEqualTo(quoteId.intValue());
        assertThat(response.getBody().get("quoteCurrencyCode")).isEqualTo("QDB");
        assertThat(response.getBody().get("precision")).isEqualTo(4);

        List<Map> currencyPairs = (List<Map>) response.getBody().get("currencyPairs");
        assertThat(currencyPairs).hasSize(7);
        for (Map pair : currencyPairs) {
            assertThat(pair.get("rateType")).isEqualTo("AUTO");
            assertThat(pair.get("rate")).isNull();
            assertThat(pair.get("active")).isEqualTo(Boolean.FALSE);
            assertThat(pair.get("brandCode")).isNotNull();
        }
        List<Object> brandCodes = currencyPairs.stream().map(p -> p.get("brandCode")).toList();
        assertThat(brandCodes).containsExactlyInAnyOrder("au", "moneta", "pug", "star", "um", "vjp", "vt");
    }

    @Test
    void createDefinitionDefaultsPrecisionToFour() {
        Long baseId = createCurrency("QDC");
        Long quoteId = createCurrency("QDD");

        ResponseEntity<Map> response = createDefinition(baseId, quoteId, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("precision")).isEqualTo(4);
    }

    @Test
    void createDefinitionWithSameBaseAndQuoteReturnsBadRequest() {
        Long baseId = createCurrency("QDE");

        ResponseEntity<Map> response = createDefinition(baseId, baseId, 4);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createDefinitionWithNonExistentCurrencyReturnsBadRequest() {
        Long baseId = createCurrency("QDF");

        ResponseEntity<Map> response = createDefinition(baseId, 9999999L, 4);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createDefinitionWithOutOfRangePrecisionReturnsBadRequest() {
        Long baseId = createCurrency("QDG");
        Long quoteId = createCurrency("QDH");

        ResponseEntity<Map> response = createDefinition(baseId, quoteId, 9);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createDefinitionWithDuplicatePairReturnsConflict() {
        Long baseId = createCurrency("QDI");
        Long quoteId = createCurrency("QDJ");
        ResponseEntity<Map> first = createDefinition(baseId, quoteId, 4);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map> second = createDefinition(baseId, quoteId, 6);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void listDefinitionsIncludesCreatedDefinition() {
        Long baseId = createCurrency("QDK");
        Long quoteId = createCurrency("QDL");
        ResponseEntity<Map> created = createDefinition(baseId, quoteId, 4);
        Long id = ((Number) created.getBody().get("id")).longValue();

        ResponseEntity<Map[]> response = restTemplate.getForEntity(definitionsUrl(), Map[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Long> ids = List.of(response.getBody()).stream().map(d -> ((Number) d.get("id")).longValue()).toList();
        assertThat(ids).contains(id);
    }

    @Test
    void getDefinitionByIdReturnsDefinition() {
        Long baseId = createCurrency("QDM");
        Long quoteId = createCurrency("QDN");
        ResponseEntity<Map> created = createDefinition(baseId, quoteId, 4);
        Long id = ((Number) created.getBody().get("id")).longValue();

        ResponseEntity<Map> response = restTemplate.getForEntity(definitionsUrl() + "/" + id, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("baseCurrencyCode")).isEqualTo("QDM");
        assertThat(response.getBody().get("quoteCurrencyCode")).isEqualTo("QDN");
    }

    @Test
    void updateDefinitionUpdatesPrecisionOnlyAndIgnoresCurrencyIds() {
        Long baseId = createCurrency("QDO");
        Long quoteId = createCurrency("QDP");
        ResponseEntity<Map> created = createDefinition(baseId, quoteId, 4);
        Long id = ((Number) created.getBody().get("id")).longValue();

        ResponseEntity<Map> response = restTemplate.exchange(definitionsUrl() + "/" + id, HttpMethod.PUT,
                jsonEntity("{\"baseCurrencyId\": 999999, \"quoteCurrencyId\": 999999, \"precision\": 6}"),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("precision")).isEqualTo(6);
        assertThat(response.getBody().get("baseCurrencyId")).isEqualTo(baseId.intValue());
        assertThat(response.getBody().get("quoteCurrencyId")).isEqualTo(quoteId.intValue());
    }

    @Test
    void updateDefinitionWithOutOfRangePrecisionReturnsBadRequest() {
        Long baseId = createCurrency("QDQ");
        Long quoteId = createCurrency("QDR");
        ResponseEntity<Map> created = createDefinition(baseId, quoteId, 4);
        Long id = ((Number) created.getBody().get("id")).longValue();

        ResponseEntity<String> response = restTemplate.exchange(definitionsUrl() + "/" + id, HttpMethod.PUT,
                jsonEntity("{\"precision\": 9}"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateDefinitionForUnknownIdReturnsNotFound() {
        ResponseEntity<String> response = restTemplate.exchange(definitionsUrl() + "/999999", HttpMethod.PUT,
                jsonEntity("{\"precision\": 4}"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteDefinitionForUnknownIdReturnsNotFound() {
        ResponseEntity<String> response = restTemplate.exchange(definitionsUrl() + "/999999", HttpMethod.DELETE,
                null, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteDefinitionWithActiveBrandPairReturnsConflictAndDeletesNothing() {
        Long baseId = createCurrency("QDS");
        Long quoteId = createCurrency("QDT");
        ResponseEntity<Map> created = createDefinition(baseId, quoteId, 4);
        Long id = ((Number) created.getBody().get("id")).longValue();

        // Simulate the sibling currency-pair API activating one brand's pair.
        jdbcTemplate.update(
                "UPDATE currency_pair SET active = TRUE WHERE currency_pair_definition_id = ? LIMIT 1", id);

        ResponseEntity<Map> response = restTemplate.exchange(definitionsUrl() + "/" + id, HttpMethod.DELETE,
                null, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("error")).isEqualTo("Active brand currency pairs exist");
        assertThat((List<Object>) response.getBody().get("activeBrandCodes")).isNotEmpty();

        // Confirm nothing was deleted.
        ResponseEntity<Map> getResponse = restTemplate.getForEntity(definitionsUrl() + "/" + id, Map.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Deactivate so cleanup can delete it.
        jdbcTemplate.update(
                "UPDATE currency_pair SET active = FALSE WHERE currency_pair_definition_id = ?", id);
    }

    @Test
    void deleteDefinitionWithoutActivePairsSucceedsAndCascadesCurrencyPairs() {
        Long baseId = createCurrency("QDU");
        Long quoteId = createCurrency("QDV");
        ResponseEntity<Map> created = createDefinition(baseId, quoteId, 4);
        Long id = ((Number) created.getBody().get("id")).longValue();
        createdDefinitionIds.remove(id);

        ResponseEntity<Void> response = restTemplate.exchange(definitionsUrl() + "/" + id, HttpMethod.DELETE,
                null, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> getResponse = restTemplate.getForEntity(definitionsUrl() + "/" + id, String.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        Integer remainingPairs = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM currency_pair WHERE currency_pair_definition_id = ?", Integer.class, id);
        assertThat(remainingPairs).isZero();
    }
}
