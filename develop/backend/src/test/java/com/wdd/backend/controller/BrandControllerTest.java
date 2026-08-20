package com.wdd.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
class BrandControllerTest {

    @LocalServerPort
    private int port;

    private final TestRestTemplate restTemplate = new TestRestTemplate();

    private String baseUrl() {
        return "http://localhost:" + port + "/api/brands";
    }

    private Long vtBrandId;
    private Boolean vtOriginalActive;

    @BeforeEach
    void captureVtBrandState() {
        ResponseEntity<Map[]> response = restTemplate.getForEntity(baseUrl(), Map[].class);
        for (Map<?, ?> brand : response.getBody()) {
            if ("vt".equals(brand.get("code"))) {
                vtBrandId = ((Number) brand.get("id")).longValue();
                vtOriginalActive = (Boolean) brand.get("active");
            }
        }
    }

    @AfterEach
    void restoreVtBrandState() {
        if (vtBrandId != null && vtOriginalActive != null) {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(
                    "{\"active\": " + vtOriginalActive + "}", headers);
            restTemplate.exchange(baseUrl() + "/" + vtBrandId, HttpMethod.PUT, entity, String.class);
        }
    }

    @Test
    void listAllBrandsReturnsSevenSeededBrands() {
        ResponseEntity<Map[]> response = restTemplate.getForEntity(baseUrl(), Map[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(7);

        List<Object> codes = List.of(response.getBody()).stream().map(b -> b.get("code")).toList();
        assertThat(codes).containsExactlyInAnyOrder(
                "au", "moneta", "pug", "star", "um", "vjp", "vt");
    }

    @Test
    void listBrandsFilteredByActiveTrueReturnsOnlyActiveBrands() {
        ResponseEntity<Map[]> response = restTemplate.getForEntity(baseUrl() + "?active=true", Map[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        for (Map<?, ?> brand : response.getBody()) {
            assertThat(brand.get("active")).isEqualTo(Boolean.TRUE);
        }
    }

    @Test
    void listBrandsFilteredByActiveFalseExcludesActiveBrands() {
        // Flip vt off first so there is at least one inactive brand to find.
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>("{\"active\": false}", headers);
        restTemplate.exchange(baseUrl() + "/" + vtBrandId, HttpMethod.PUT, entity, String.class);

        ResponseEntity<Map[]> response = restTemplate.getForEntity(baseUrl() + "?active=false", Map[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        for (Map<?, ?> brand : response.getBody()) {
            assertThat(brand.get("active")).isEqualTo(Boolean.FALSE);
        }
        List<Object> codes = List.of(response.getBody()).stream().map(b -> b.get("code")).toList();
        assertThat(codes).contains("vt");
    }

    @Test
    void getBrandByIdReturnsNotFoundForUnknownId() {
        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl() + "/999999", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getBrandByIdReturnsBrand() {
        ResponseEntity<Map> response = restTemplate.getForEntity(baseUrl() + "/" + vtBrandId, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("code")).isEqualTo("vt");
    }

    @Test
    void updateBrandActiveFlipsAndPersistsValue() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>("{\"active\": false}", headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/" + vtBrandId, HttpMethod.PUT, entity, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("active")).isEqualTo(Boolean.FALSE);

        // Verify persisted via a fresh GET.
        ResponseEntity<Map> getResponse = restTemplate.getForEntity(baseUrl() + "/" + vtBrandId, Map.class);
        assertThat(getResponse.getBody().get("active")).isEqualTo(Boolean.FALSE);
    }

    @Test
    void updateBrandWithMissingActiveReturnsBadRequest() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>("{}", headers);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/" + vtBrandId, HttpMethod.PUT, entity, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateBrandForUnknownIdReturnsNotFound() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>("{\"active\": true}", headers);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/999999", HttpMethod.PUT, entity, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
