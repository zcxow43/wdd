package com.wdd.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CurrencyControllerTest {

    @LocalServerPort
    private int port;

    private final TestRestTemplate restTemplate = new TestRestTemplate();

    private final List<Long> createdIds = new ArrayList<>();

    private String baseUrl() {
        return "http://localhost:" + port + "/api/currencies";
    }

    @AfterEach
    void cleanUp() {
        for (Long id : createdIds) {
            restTemplate.delete(baseUrl() + "/" + id);
        }
        createdIds.clear();
    }

    private ResponseEntity<Map> createCurrency(String code, String name, String symbol, Integer decimalPlaces) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = String.format(
                "{\"code\": \"%s\", \"name\": \"%s\", \"symbol\": \"%s\", \"decimalPlaces\": %d}",
                code, name, symbol, decimalPlaces);
        HttpEntity<String> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(baseUrl(), entity, Map.class);
        if (response.getStatusCode() == HttpStatus.CREATED) {
            createdIds.add(((Number) response.getBody().get("id")).longValue());
        }
        return response;
    }

    @Test
    void listCurrenciesReturnsAllCreatedCurrencies() {
        ResponseEntity<Map> created = createCurrency("QAA", "Quality Assurance Dollar", "Q$", 2);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map[]> response = restTemplate.getForEntity(baseUrl(), Map[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Object> codes = List.of(response.getBody()).stream().map(c -> c.get("code")).toList();
        assertThat(codes).contains("QAA");
    }

    @Test
    void getCurrencyByIdReturnsNotFoundForUnknownId() {
        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl() + "/999999", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getCurrencyByIdReturnsCurrency() {
        ResponseEntity<Map> created = createCurrency("QAB", "Quality Assurance Bee", "Q#", 2);
        Long id = ((Number) created.getBody().get("id")).longValue();

        ResponseEntity<Map> response = restTemplate.getForEntity(baseUrl() + "/" + id, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("code")).isEqualTo("QAB");
    }

    @Test
    void createCurrencyReturnsCreatedObject() {
        ResponseEntity<Map> response = createCurrency("QAC", "Quality Assurance Coin", "Q&", 3);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("code")).isEqualTo("QAC");
        assertThat(response.getBody().get("name")).isEqualTo("Quality Assurance Coin");
        assertThat(response.getBody().get("symbol")).isEqualTo("Q&");
        assertThat(response.getBody().get("decimalPlaces")).isEqualTo(3);
        assertThat(response.getBody().get("id")).isNotNull();
    }

    @Test
    void createCurrencyWithDuplicateCodeReturnsConflict() {
        ResponseEntity<Map> first = createCurrency("QAD", "First", "$", 2);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> second = restTemplate.postForEntity(baseUrl(),
                jsonEntity("{\"code\": \"QAD\", \"name\": \"Second\", \"symbol\": \"$\", \"decimalPlaces\": 2}"),
                String.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void createCurrencyWithInvalidCodeFormatReturnsBadRequest() {
        ResponseEntity<String> response = restTemplate.postForEntity(baseUrl(),
                jsonEntity("{\"code\": \"usd\", \"name\": \"US Dollar\", \"symbol\": \"$\", \"decimalPlaces\": 2}"),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createCurrencyWithMissingNameReturnsBadRequest() {
        ResponseEntity<String> response = restTemplate.postForEntity(baseUrl(),
                jsonEntity("{\"code\": \"QAE\", \"symbol\": \"$\", \"decimalPlaces\": 2}"),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createCurrencyWithMissingSymbolReturnsBadRequest() {
        ResponseEntity<String> response = restTemplate.postForEntity(baseUrl(),
                jsonEntity("{\"code\": \"QAF\", \"name\": \"Name\", \"decimalPlaces\": 2}"),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createCurrencyWithDecimalPlacesOutOfRangeReturnsBadRequest() {
        ResponseEntity<String> response = restTemplate.postForEntity(baseUrl(),
                jsonEntity("{\"code\": \"QAG\", \"name\": \"Name\", \"symbol\": \"$\", \"decimalPlaces\": 9}"),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateCurrencyUpdatesFieldsAndKeepsCodeUnchanged() {
        ResponseEntity<Map> created = createCurrency("QAH", "Original Name", "$", 2);
        Long id = ((Number) created.getBody().get("id")).longValue();

        ResponseEntity<Map> response = restTemplate.exchange(baseUrl() + "/" + id, HttpMethod.PUT,
                jsonEntity("{\"code\": \"ZZZ\", \"name\": \"Updated Name\", \"symbol\": \"Z\", \"decimalPlaces\": 4}"),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("code")).isEqualTo("QAH");
        assertThat(response.getBody().get("name")).isEqualTo("Updated Name");
        assertThat(response.getBody().get("symbol")).isEqualTo("Z");
        assertThat(response.getBody().get("decimalPlaces")).isEqualTo(4);

        ResponseEntity<Map> getResponse = restTemplate.getForEntity(baseUrl() + "/" + id, Map.class);
        assertThat(getResponse.getBody().get("code")).isEqualTo("QAH");
        assertThat(getResponse.getBody().get("name")).isEqualTo("Updated Name");
    }

    @Test
    void updateCurrencyForUnknownIdReturnsNotFound() {
        ResponseEntity<String> response = restTemplate.exchange(baseUrl() + "/999999", HttpMethod.PUT,
                jsonEntity("{\"name\": \"Name\", \"symbol\": \"$\", \"decimalPlaces\": 2}"),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteCurrencyRemovesRowAndReturnsNoContent() {
        ResponseEntity<Map> created = createCurrency("QAI", "To Delete", "$", 2);
        Long id = ((Number) created.getBody().get("id")).longValue();
        createdIds.remove(id);

        ResponseEntity<Void> response = restTemplate.exchange(baseUrl() + "/" + id, HttpMethod.DELETE,
                null, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> getResponse = restTemplate.getForEntity(baseUrl() + "/" + id, String.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteCurrencyForUnknownIdReturnsNotFound() {
        ResponseEntity<String> response = restTemplate.exchange(baseUrl() + "/999999", HttpMethod.DELETE,
                null, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private HttpEntity<String> jsonEntity(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
