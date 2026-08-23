package com.wdd.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
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

/**
 * {@code GET /api/spreads/effective} always resolves from committed data —
 * pending audit requests raised through {@code /api/brand-spreads} or
 * {@code /api/spread-groups} never affect it. This test drives every write
 * involved through the real submit-then-approve flow to prove the endpoint
 * stays identical before a submit and only changes after approval.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SpreadControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final TestRestTemplate restTemplate = new TestRestTemplate();

    private final List<Long> createdGroupIds = new ArrayList<>();
    private final List<Long> createdAuditRequestIds = new ArrayList<>();
    private final List<Long> createdDefinitionIds = new ArrayList<>();
    private final List<Long> createdCurrencyIds = new ArrayList<>();

    private String effectiveUrl() {
        return "http://localhost:" + port + "/api/spreads/effective";
    }

    private String groupsUrl() {
        return "http://localhost:" + port + "/api/spread-groups";
    }

    private String definitionsUrl() {
        return "http://localhost:" + port + "/api/currency-pair-definitions";
    }

    private String currenciesUrl() {
        return "http://localhost:" + port + "/api/currencies";
    }

    private String brandSpreadsUrl() {
        return "http://localhost:" + port + "/api/brand-spreads";
    }

    private String auditRequestsUrl() {
        return "http://localhost:" + port + "/api/audit-requests";
    }

    @AfterEach
    void cleanUp() {
        for (Long id : createdGroupIds) {
            jdbcTemplate.update("DELETE FROM spread_group WHERE id = ?", id);
        }
        createdGroupIds.clear();
        for (Long id : createdAuditRequestIds) {
            jdbcTemplate.update("DELETE FROM audit_request WHERE id = ?", id);
        }
        createdAuditRequestIds.clear();
        for (Long id : createdDefinitionIds) {
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

    private Long createDefinition(Long baseCurrencyId, Long quoteCurrencyId) {
        String body = String.format(
                "{\"baseCurrencyId\": %d, \"quoteCurrencyId\": %d, \"precision\": 4}",
                baseCurrencyId, quoteCurrencyId);
        ResponseEntity<Map> response = restTemplate.postForEntity(definitionsUrl(), jsonEntity(body), Map.class);
        Long id = ((Number) response.getBody().get("id")).longValue();
        createdDefinitionIds.add(id);
        return id;
    }

    private Long firstBrandId() {
        return jdbcTemplate.queryForObject("SELECT id FROM brand ORDER BY id LIMIT 1", Long.class);
    }

    private Long currencyPairIdFor(Long definitionId, Long brandId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM currency_pair WHERE currency_pair_definition_id = ? AND brand_id = ?",
                Long.class, definitionId, brandId);
    }

    private ResponseEntity<Map> approve(Long auditRequestId) {
        return restTemplate.postForEntity(auditRequestsUrl() + "/" + auditRequestId + "/approve",
                jsonEntity("{}"), Map.class);
    }

    private Long submitAndApprove(ResponseEntity<Map> pendingResponse) {
        assertThat(pendingResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        Long auditRequestId = ((Number) pendingResponse.getBody().get("auditRequestId")).longValue();
        createdAuditRequestIds.add(auditRequestId);
        ResponseEntity<Map> approved = approve(auditRequestId);
        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
        return auditRequestId;
    }

    private Long createApprovedGroup(Long brandId, String name, String depositSpread, String withdrawalSpread) {
        String body = String.format("{\"brandId\": %d, \"name\": \"%s\", \"depositSpread\": %s, "
                + "\"withdrawalSpread\": %s}", brandId, name, depositSpread, withdrawalSpread);
        ResponseEntity<Map> response = restTemplate.postForEntity(groupsUrl(), jsonEntity(body), Map.class);
        submitAndApprove(response);
        Long groupId = jdbcTemplate.queryForObject(
                "SELECT id FROM spread_group WHERE brand_id = ? AND name = ?", Long.class, brandId, name);
        createdGroupIds.add(groupId);
        return groupId;
    }

    private void assignMembersApproved(Long groupId, List<Long> currencyPairIds) {
        String ids = currencyPairIds.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
        String body = "{\"currencyPairIds\": [" + ids + "]}";
        ResponseEntity<Map> response = restTemplate.postForEntity(groupsUrl() + "/" + groupId + "/members",
                jsonEntity(body), Map.class);
        submitAndApprove(response);
    }

    private void updateBrandSpreadApproved(Long brandId, String depositSpread, String withdrawalSpread) {
        ResponseEntity<Map> response = restTemplate.exchange(brandSpreadsUrl() + "/" + brandId, HttpMethod.PUT,
                jsonEntity(String.format("{\"depositSpread\": %s, \"withdrawalSpread\": %s}", depositSpread,
                        withdrawalSpread)),
                Map.class);
        submitAndApprove(response);
    }

    @Test
    void missingBrandIdReturnsBadRequest() {
        ResponseEntity<String> response = restTemplate.getForEntity(effectiveUrl(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unknownBrandIdReturnsNotFound() {
        ResponseEntity<String> response = restTemplate.getForEntity(effectiveUrl() + "?brandId=999999",
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void returnsGroupSourceForAssignedPairsAndDefaultSourceForUnassignedOnes() {
        Long brandId = firstBrandId();

        // Snapshot and set a known brand default so DEFAULT-source values are deterministic.
        ResponseEntity<Map> originalBrandSpread = restTemplate.getForEntity(brandSpreadsUrl() + "/" + brandId,
                Map.class);
        BigDecimal originalDeposit = new BigDecimal(originalBrandSpread.getBody().get("depositSpread").toString());
        BigDecimal originalWithdrawal = new BigDecimal(
                originalBrandSpread.getBody().get("withdrawalSpread").toString());
        try {
            updateBrandSpreadApproved(brandId, "0.0005", "0.0008");

            Long baseId1 = createCurrency("EFA");
            Long quoteId1 = createCurrency("EFB");
            Long definitionId1 = createDefinition(baseId1, quoteId1);
            Long groupedPairId = currencyPairIdFor(definitionId1, brandId);

            Long baseId2 = createCurrency("EFC");
            Long quoteId2 = createCurrency("EFD");
            Long definitionId2 = createDefinition(baseId2, quoteId2);
            Long defaultPairId = currencyPairIdFor(definitionId2, brandId);

            Long groupId = createApprovedGroup(brandId, "EFF-" + System.nanoTime(), "0.0002", "0.0003");
            assignMembersApproved(groupId, List.of(groupedPairId));

            ResponseEntity<Map[]> response = restTemplate.getForEntity(effectiveUrl() + "?brandId=" + brandId,
                    Map[].class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            Map<?, ?> groupedEntry = findByCurrencyPairId(response.getBody(), groupedPairId);
            assertThat(groupedEntry.get("source")).isEqualTo("GROUP");
            assertThat(((Number) groupedEntry.get("spreadGroupId")).longValue()).isEqualTo(groupId);
            assertThat(new BigDecimal(groupedEntry.get("depositSpread").toString()))
                    .isEqualByComparingTo("0.0002");
            assertThat(new BigDecimal(groupedEntry.get("withdrawalSpread").toString()))
                    .isEqualByComparingTo("0.0003");

            Map<?, ?> defaultEntry = findByCurrencyPairId(response.getBody(), defaultPairId);
            assertThat(defaultEntry.get("source")).isEqualTo("DEFAULT");
            assertThat(defaultEntry.get("spreadGroupId")).isNull();
            assertThat(new BigDecimal(defaultEntry.get("depositSpread").toString()))
                    .isEqualByComparingTo("0.0005");
            assertThat(new BigDecimal(defaultEntry.get("withdrawalSpread").toString()))
                    .isEqualByComparingTo("0.0008");
        } finally {
            updateBrandSpreadApproved(brandId, originalDeposit.toPlainString(), originalWithdrawal.toPlainString());
        }
    }

    @Test
    void effectiveSpreadsAreUnchangedImmediatelyAfterSubmitAndOnlyChangeAfterApproval() {
        Long brandId = firstBrandId();

        ResponseEntity<Map> originalBrandSpread = restTemplate.getForEntity(brandSpreadsUrl() + "/" + brandId,
                Map.class);
        BigDecimal originalDeposit = new BigDecimal(originalBrandSpread.getBody().get("depositSpread").toString());
        BigDecimal originalWithdrawal = new BigDecimal(
                originalBrandSpread.getBody().get("withdrawalSpread").toString());
        try {
            Long baseId = createCurrency("EFG");
            Long quoteId = createCurrency("EFH");
            Long definitionId = createDefinition(baseId, quoteId);
            Long pairId = currencyPairIdFor(definitionId, brandId);

            ResponseEntity<Map[]> before = restTemplate.getForEntity(effectiveUrl() + "?brandId=" + brandId,
                    Map[].class);
            Map<?, ?> beforeEntry = findByCurrencyPairId(before.getBody(), pairId);
            BigDecimal beforeDeposit = new BigDecimal(beforeEntry.get("depositSpread").toString());
            BigDecimal beforeWithdrawal = new BigDecimal(beforeEntry.get("withdrawalSpread").toString());
            assertThat(beforeEntry.get("source")).isEqualTo("DEFAULT");

            // Submit a brand-spread change but do not approve yet.
            ResponseEntity<Map> submitResponse = restTemplate.exchange(brandSpreadsUrl() + "/" + brandId,
                    HttpMethod.PUT, jsonEntity("{\"depositSpread\": 0.0099, \"withdrawalSpread\": 0.0088}"),
                    Map.class);
            assertThat(submitResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
            Long auditRequestId = ((Number) submitResponse.getBody().get("auditRequestId")).longValue();
            createdAuditRequestIds.add(auditRequestId);

            // Effective spreads must be identical to before the submit.
            ResponseEntity<Map[]> afterSubmit = restTemplate.getForEntity(effectiveUrl() + "?brandId=" + brandId,
                    Map[].class);
            Map<?, ?> afterSubmitEntry = findByCurrencyPairId(afterSubmit.getBody(), pairId);
            assertThat(new BigDecimal(afterSubmitEntry.get("depositSpread").toString()))
                    .isEqualByComparingTo(beforeDeposit);
            assertThat(new BigDecimal(afterSubmitEntry.get("withdrawalSpread").toString()))
                    .isEqualByComparingTo(beforeWithdrawal);
            assertThat(afterSubmitEntry.get("source")).isEqualTo("DEFAULT");

            // Only after approval do effective spreads change.
            ResponseEntity<Map> approveResponse = approve(auditRequestId);
            assertThat(approveResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            ResponseEntity<Map[]> afterApprove = restTemplate.getForEntity(effectiveUrl() + "?brandId=" + brandId,
                    Map[].class);
            Map<?, ?> afterApproveEntry = findByCurrencyPairId(afterApprove.getBody(), pairId);
            assertThat(new BigDecimal(afterApproveEntry.get("depositSpread").toString()))
                    .isEqualByComparingTo("0.0099");
            assertThat(new BigDecimal(afterApproveEntry.get("withdrawalSpread").toString()))
                    .isEqualByComparingTo("0.0088");
        } finally {
            updateBrandSpreadApproved(brandId, originalDeposit.toPlainString(), originalWithdrawal.toPlainString());
        }
    }

    private static Map<?, ?> findByCurrencyPairId(Map[] entries, Long currencyPairId) {
        for (Map<?, ?> entry : entries) {
            if (((Number) entry.get("currencyPairId")).longValue() == currencyPairId) {
                return entry;
            }
        }
        throw new AssertionError("No effective spread entry found for currencyPairId=" + currencyPairId);
    }
}
