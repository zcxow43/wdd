package com.wdd.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

/**
 * Integration tests against the real DB. {@code PUT /api/brand-spreads/{brandId}}
 * is now audited: it returns {@code 202} with a pending {@code audit_request}
 * row and changes nothing until approved via
 * {@code POST /api/audit-requests/{id}/approve} — see
 * {@code BrandSpreadAuditHandler}. Reads remain direct/unaffected.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BrandSpreadControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final TestRestTemplate restTemplate = new TestRestTemplate();

    private Long brandId;
    private BigDecimal originalDeposit;
    private BigDecimal originalWithdrawal;
    private boolean rowExisted;

    private String brandSpreadsUrl() {
        return "http://localhost:" + port + "/api/brand-spreads";
    }

    private String auditRequestsUrl() {
        return "http://localhost:" + port + "/api/audit-requests";
    }

    @BeforeEach
    void captureOriginalState() {
        brandId = jdbcTemplate.queryForObject("SELECT id FROM brand ORDER BY id LIMIT 1", Long.class);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT deposit_spread, withdrawal_spread FROM brand_spread WHERE brand_id = ?", brandId);
        rowExisted = !rows.isEmpty();
        if (rowExisted) {
            originalDeposit = (BigDecimal) rows.get(0).get("deposit_spread");
            originalWithdrawal = (BigDecimal) rows.get(0).get("withdrawal_spread");
        }
    }

    @AfterEach
    void restoreOriginalState() {
        jdbcTemplate.update("DELETE FROM audit_request WHERE entity_type = 'BRAND_SPREAD' AND entity_id = ?",
                brandId);
        if (rowExisted) {
            jdbcTemplate.update("UPDATE brand_spread SET deposit_spread = ?, withdrawal_spread = ? WHERE brand_id = ?",
                    originalDeposit, originalWithdrawal, brandId);
        } else {
            jdbcTemplate.update("DELETE FROM brand_spread WHERE brand_id = ?", brandId);
        }
    }

    private HttpEntity<String> jsonEntity(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private ResponseEntity<Map> putBrandSpread(Long brandId, String body) {
        return restTemplate.exchange(brandSpreadsUrl() + "/" + brandId, HttpMethod.PUT, jsonEntity(body), Map.class);
    }

    private ResponseEntity<Map> approve(Long auditRequestId) {
        return restTemplate.postForEntity(auditRequestsUrl() + "/" + auditRequestId + "/approve",
                jsonEntity("{}"), Map.class);
    }

    @Test
    void listReturnsOneEntryPerBrandAndFiltersByBrandId() {
        long brandCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM brand", Long.class);

        ResponseEntity<Map[]> all = restTemplate.getForEntity(brandSpreadsUrl(), Map[].class);
        assertThat(all.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(all.getBody().length).isEqualTo((int) brandCount);

        ResponseEntity<Map[]> filtered = restTemplate.getForEntity(
                brandSpreadsUrl() + "?brandId=" + brandId, Map[].class);
        assertThat(filtered.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(filtered.getBody()).hasSize(1);
        assertThat(((Number) filtered.getBody()[0].get("brandId")).longValue()).isEqualTo(brandId);
    }

    @Test
    void getByIdReturnsNotFoundForUnknownBrand() {
        ResponseEntity<String> response = restTemplate.getForEntity(brandSpreadsUrl() + "/999999", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getByIdAutoCreatesZeroRowWhenMissing() {
        jdbcTemplate.update("DELETE FROM brand_spread WHERE brand_id = ?", brandId);

        ResponseEntity<Map> response = restTemplate.getForEntity(brandSpreadsUrl() + "/" + brandId, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new BigDecimal(response.getBody().get("depositSpread").toString()))
                .isEqualByComparingTo("0");
        assertThat(new BigDecimal(response.getBody().get("withdrawalSpread").toString()))
                .isEqualByComparingTo("0");

        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM brand_spread WHERE brand_id = ?", Long.class,
                brandId);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void updateReturns202AndAppliesBothSpreadsOnlyAfterApproval() {
        ResponseEntity<Map> response = putBrandSpread(brandId,
                "{\"depositSpread\": 0.0005, \"withdrawalSpread\": 0.0008}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody().get("status")).isEqualTo("PENDING");
        assertThat(response.getBody().get("entityType")).isEqualTo("BRAND_SPREAD");
        assertThat(response.getBody().get("actionType")).isEqualTo("UPDATE");
        assertThat(((Number) response.getBody().get("entityId")).longValue()).isEqualTo(brandId);
        Long auditRequestId = ((Number) response.getBody().get("auditRequestId")).longValue();

        // Row must be unchanged until approved.
        ResponseEntity<Map> beforeApprove = restTemplate.getForEntity(brandSpreadsUrl() + "/" + brandId, Map.class);
        if (rowExisted) {
            assertThat(new BigDecimal(beforeApprove.getBody().get("depositSpread").toString()))
                    .isEqualByComparingTo(originalDeposit);
        }

        ResponseEntity<Map> approveResponse = approve(auditRequestId);
        assertThat(approveResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(approveResponse.getBody().get("status")).isEqualTo("APPROVED");

        ResponseEntity<Map> afterApprove = restTemplate.getForEntity(brandSpreadsUrl() + "/" + brandId, Map.class);
        assertThat(new BigDecimal(afterApprove.getBody().get("depositSpread").toString()))
                .isEqualByComparingTo("0.0005");
        assertThat(new BigDecimal(afterApprove.getBody().get("withdrawalSpread").toString()))
                .isEqualByComparingTo("0.0008");
    }

    @Test
    void updateRejectsNegativeValue() {
        ResponseEntity<String> response = restTemplate.exchange(brandSpreadsUrl() + "/" + brandId, HttpMethod.PUT,
                jsonEntity("{\"depositSpread\": -0.0001, \"withdrawalSpread\": 0.0008}"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateRejectsMoreThanEightDecimalPlaces() {
        ResponseEntity<String> response = restTemplate.exchange(brandSpreadsUrl() + "/" + brandId, HttpMethod.PUT,
                jsonEntity("{\"depositSpread\": 0.000000001, \"withdrawalSpread\": 0.0008}"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateReturnsNotFoundForUnknownBrand() {
        ResponseEntity<String> response = restTemplate.exchange(brandSpreadsUrl() + "/999999", HttpMethod.PUT,
                jsonEntity("{\"depositSpread\": 0.0001, \"withdrawalSpread\": 0.0002}"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void secondUpdateWhilePendingReturns409() {
        ResponseEntity<Map> first = putBrandSpread(brandId,
                "{\"depositSpread\": 0.0001, \"withdrawalSpread\": 0.0002}");
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        ResponseEntity<String> second = restTemplate.exchange(brandSpreadsUrl() + "/" + brandId, HttpMethod.PUT,
                jsonEntity("{\"depositSpread\": 0.0003, \"withdrawalSpread\": 0.0004}"), String.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
