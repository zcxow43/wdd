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

/**
 * Integration tests against the real DB. Every write on this API is now
 * audited ({@code POST}/{@code PUT}/{@code DELETE} return {@code 202} with
 * a pending {@code audit_request} row and change nothing until approved via
 * {@code POST /api/audit-requests/{id}/approve} — see {@code CurrencyPairAuditHandler}).
 * The fan-out on {@code POST /api/currency-pair-definitions} and the
 * cascade on its delete remain unaudited direct writes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CurrencyPairControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final TestRestTemplate restTemplate = new TestRestTemplate();

    private final List<Long> createdCurrencyPairIds = new ArrayList<>();
    private final List<Long> createdAuditRequestIds = new ArrayList<>();
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

    private String auditRequestsUrl() {
        return "http://localhost:" + port + "/api/audit-requests";
    }

    @AfterEach
    void cleanUp() {
        for (Long id : createdCurrencyPairIds) {
            jdbcTemplate.update("DELETE FROM currency_pair WHERE id = ?", id);
        }
        createdCurrencyPairIds.clear();
        for (Long id : createdAuditRequestIds) {
            jdbcTemplate.update("DELETE FROM audit_request WHERE id = ?", id);
        }
        createdAuditRequestIds.clear();
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

    private ResponseEntity<Map> postCurrencyPair(Long definitionId, Long brandId, String rateType,
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
        if (response.getStatusCode() == HttpStatus.ACCEPTED) {
            createdAuditRequestIds.add(((Number) response.getBody().get("auditRequestId")).longValue());
        }
        return response;
    }

    private ResponseEntity<Map> putCurrencyPair(Long id, String body) {
        ResponseEntity<Map> response = restTemplate.exchange(currencyPairsUrl() + "/" + id, HttpMethod.PUT,
                jsonEntity(body), Map.class);
        if (response.getStatusCode() == HttpStatus.ACCEPTED) {
            createdAuditRequestIds.add(((Number) response.getBody().get("auditRequestId")).longValue());
        }
        return response;
    }

    private ResponseEntity<Map> deleteCurrencyPair(Long id) {
        ResponseEntity<Map> response = restTemplate.exchange(currencyPairsUrl() + "/" + id, HttpMethod.DELETE, null,
                Map.class);
        if (response.getStatusCode() == HttpStatus.ACCEPTED) {
            createdAuditRequestIds.add(((Number) response.getBody().get("auditRequestId")).longValue());
        }
        return response;
    }

    private ResponseEntity<Map> approve(Long auditRequestId) {
        return restTemplate.postForEntity(auditRequestsUrl() + "/" + auditRequestId + "/approve",
                jsonEntity("{}"), Map.class);
    }

    private ResponseEntity<Map> reject(Long auditRequestId) {
        return restTemplate.postForEntity(auditRequestsUrl() + "/" + auditRequestId + "/reject",
                jsonEntity("{\"comment\": \"no\"}"), Map.class);
    }

    private ResponseEntity<Map> cancel(Long auditRequestId) {
        return restTemplate.postForEntity(auditRequestsUrl() + "/" + auditRequestId + "/cancel",
                jsonEntity("{}"), Map.class);
    }

    private Long findPairId(Long definitionId, Long brandId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM currency_pair WHERE currency_pair_definition_id = ? AND brand_id = ?", Long.class,
                definitionId, brandId);
    }

    private Integer countPairs(Long definitionId, Long brandId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM currency_pair WHERE currency_pair_definition_id = ? AND brand_id = ?",
                Integer.class, definitionId, brandId);
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
    void createReturns202AndCreatesRowOnlyAfterApproval() {
        Long baseId = createCurrency("QPC");
        Long quoteId = createCurrency("QPD");
        Long definitionId = createDefinition(baseId, quoteId, 4);
        deleteFannedOutPairs(definitionId);
        Long brandId = firstBrandId();

        ResponseEntity<Map> response = postCurrencyPair(definitionId, brandId, "AUTO", "150.25", false);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody().get("status")).isEqualTo("PENDING");
        assertThat(response.getBody().get("entityType")).isEqualTo("CURRENCY_PAIR");
        assertThat(response.getBody().get("actionType")).isEqualTo("CREATE");
        assertThat(response.getBody().get("entityId")).isNull();
        Long auditRequestId = ((Number) response.getBody().get("auditRequestId")).longValue();

        // Row must not exist yet.
        assertThat(countPairs(definitionId, brandId)).isEqualTo(0);

        ResponseEntity<Map> approveResponse = approve(auditRequestId);
        assertThat(approveResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(approveResponse.getBody().get("status")).isEqualTo("APPROVED");

        Long pairId = findPairId(definitionId, brandId);
        createdCurrencyPairIds.add(pairId);
        ResponseEntity<Map> pair = restTemplate.getForEntity(currencyPairsUrl() + "/" + pairId, Map.class);
        assertThat(pair.getBody().get("rateType")).isEqualTo("AUTO");
        assertThat(pair.getBody().get("rate")).isNull();
        assertThat(pair.getBody().get("baseCurrencyCode")).isEqualTo("QPC");
        assertThat(pair.getBody().get("quoteCurrencyCode")).isEqualTo("QPD");
    }

    @Test
    void createWithManualRateTypeAndNoRateReturnsBadRequest() {
        Long baseId = createCurrency("QPE");
        Long quoteId = createCurrency("QPF");
        Long definitionId = createDefinition(baseId, quoteId, 4);
        deleteFannedOutPairs(definitionId);
        Long brandId = firstBrandId();

        ResponseEntity<Map> response = postCurrencyPair(definitionId, brandId, "MANUAL", null, false);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createWithManualRateTypeAndNonPositiveRateReturnsBadRequest() {
        Long baseId = createCurrency("QPG");
        Long quoteId = createCurrency("QPH");
        Long definitionId = createDefinition(baseId, quoteId, 4);
        deleteFannedOutPairs(definitionId);
        Long brandId = firstBrandId();

        ResponseEntity<Map> response = postCurrencyPair(definitionId, brandId, "MANUAL", "0", false);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createWithManualRateExceedingPrecisionReturnsBadRequest() {
        Long baseId = createCurrency("QPI");
        Long quoteId = createCurrency("QPJ");
        Long definitionId = createDefinition(baseId, quoteId, 2);
        deleteFannedOutPairs(definitionId);
        Long brandId = firstBrandId();

        ResponseEntity<Map> response = postCurrencyPair(definitionId, brandId, "MANUAL", "150.255", false);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createWithDuplicateDefinitionAndBrandReturnsConflict() {
        Long baseId = createCurrency("QPK");
        Long quoteId = createCurrency("QPL");
        Long definitionId = createDefinition(baseId, quoteId, 4);
        Long brandId = firstBrandId();

        // A currency_pair row for this (definition, brand) already exists via fan-out.
        ResponseEntity<Map> response = postCurrencyPair(definitionId, brandId, "AUTO", null, false);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void createWithNonExistentDefinitionOrBrandReturnsBadRequest() {
        Long brandId = firstBrandId();

        ResponseEntity<Map> badDefinition = postCurrencyPair(999999L, brandId, "AUTO", null, false);
        assertThat(badDefinition.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        Long baseId = createCurrency("QPM");
        Long quoteId = createCurrency("QPN");
        Long definitionId = createDefinition(baseId, quoteId, 4);
        deleteFannedOutPairs(definitionId);

        ResponseEntity<Map> badBrand = postCurrencyPair(definitionId, 999999L, "AUTO", null, false);
        assertThat(badBrand.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateReturns202AndChangesRowOnlyAfterApproval() {
        Long baseId = createCurrency("QPO");
        Long quoteId = createCurrency("QPP");
        Long definitionId = createDefinition(baseId, quoteId, 4);
        deleteFannedOutPairs(definitionId);
        Long brandId = firstBrandId();
        ResponseEntity<Map> created = postCurrencyPair(definitionId, brandId, "MANUAL", "150.25", false);
        Long createAuditId = ((Number) created.getBody().get("auditRequestId")).longValue();
        approve(createAuditId);
        Long id = findPairId(definitionId, brandId);
        createdCurrencyPairIds.add(id);

        ResponseEntity<Map> response = putCurrencyPair(id, "{\"active\": true}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody().get("actionType")).isEqualTo("UPDATE");
        assertThat(response.getBody().get("entityId")).isEqualTo(id.intValue());
        Long auditRequestId = ((Number) response.getBody().get("auditRequestId")).longValue();

        // Row must be unchanged until approved.
        ResponseEntity<Map> beforeApprove = restTemplate.getForEntity(currencyPairsUrl() + "/" + id, Map.class);
        assertThat(beforeApprove.getBody().get("active")).isEqualTo(Boolean.FALSE);

        ResponseEntity<Map> approveResponse = approve(auditRequestId);
        assertThat(approveResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> afterApprove = restTemplate.getForEntity(currencyPairsUrl() + "/" + id, Map.class);
        assertThat(afterApprove.getBody().get("active")).isEqualTo(Boolean.TRUE);
        assertThat(afterApprove.getBody().get("rateType")).isEqualTo("MANUAL");
        assertThat(afterApprove.getBody().get("rate")).isEqualTo(150.25);
    }

    @Test
    void updateSwitchingRateTypeFromManualToAutoClearsRateAfterApproval() {
        Long baseId = createCurrency("QPQ");
        Long quoteId = createCurrency("QPR");
        Long definitionId = createDefinition(baseId, quoteId, 4);
        deleteFannedOutPairs(definitionId);
        Long brandId = firstBrandId();
        ResponseEntity<Map> created = postCurrencyPair(definitionId, brandId, "MANUAL", "150.25", false);
        approve(((Number) created.getBody().get("auditRequestId")).longValue());
        Long id = findPairId(definitionId, brandId);
        createdCurrencyPairIds.add(id);

        ResponseEntity<Map> response = putCurrencyPair(id, "{\"rateType\": \"AUTO\"}");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        approve(((Number) response.getBody().get("auditRequestId")).longValue());

        ResponseEntity<Map> afterApprove = restTemplate.getForEntity(currencyPairsUrl() + "/" + id, Map.class);
        assertThat(afterApprove.getBody().get("rateType")).isEqualTo("AUTO");
        assertThat(afterApprove.getBody().get("rate")).isNull();
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
    void deleteReturns202AndRemovesRowOnlyAfterApprovalRegardlessOfActiveState() {
        Long baseId = createCurrency("QPS");
        Long quoteId = createCurrency("QPT");
        Long definitionId = createDefinition(baseId, quoteId, 4);
        deleteFannedOutPairs(definitionId);
        Long brandId = firstBrandId();
        ResponseEntity<Map> created = postCurrencyPair(definitionId, brandId, "AUTO", null, true);
        approve(((Number) created.getBody().get("auditRequestId")).longValue());
        Long id = findPairId(definitionId, brandId);

        ResponseEntity<Map> beforeDelete = restTemplate.getForEntity(currencyPairsUrl() + "/" + id, Map.class);
        assertThat(beforeDelete.getBody().get("active")).isEqualTo(Boolean.TRUE);

        ResponseEntity<Map> response = deleteCurrencyPair(id);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody().get("actionType")).isEqualTo("DELETE");
        Long auditRequestId = ((Number) response.getBody().get("auditRequestId")).longValue();

        // Row still present until approved.
        ResponseEntity<Map> stillThere = restTemplate.getForEntity(currencyPairsUrl() + "/" + id, Map.class);
        assertThat(stillThere.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> approveResponse = approve(auditRequestId);
        assertThat(approveResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

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

        ResponseEntity<Map> response = postCurrencyPair(definitionId, brandId, "AUTO", null, false);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        approve(((Number) response.getBody().get("auditRequestId")).longValue());

        Long id = findPairId(definitionId, brandId);
        createdCurrencyPairIds.add(id);
        ResponseEntity<Map> pair = restTemplate.getForEntity(currencyPairsUrl() + "/" + id, Map.class);
        assertThat(pair.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pair.getBody().get("brandId")).isEqualTo(brandId.intValue());
    }

    // --- new audited-flow acceptance criteria ---

    @Test
    void secondMutationOnPairWithPendingRequestReturns409() {
        Long baseId = createCurrency("QPW");
        Long quoteId = createCurrency("QPX");
        Long definitionId = createDefinition(baseId, quoteId, 4);
        deleteFannedOutPairs(definitionId);
        Long brandId = firstBrandId();
        ResponseEntity<Map> created = postCurrencyPair(definitionId, brandId, "AUTO", null, false);
        approve(((Number) created.getBody().get("auditRequestId")).longValue());
        Long id = findPairId(definitionId, brandId);
        createdCurrencyPairIds.add(id);

        ResponseEntity<Map> firstUpdate = putCurrencyPair(id, "{\"active\": true}");
        assertThat(firstUpdate.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        ResponseEntity<String> secondUpdate = restTemplate.exchange(currencyPairsUrl() + "/" + id, HttpMethod.PUT,
                jsonEntity("{\"active\": false}"), String.class);
        assertThat(secondUpdate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<String> deleteAttempt = restTemplate.exchange(currencyPairsUrl() + "/" + id,
                HttpMethod.DELETE, null, String.class);
        assertThat(deleteAttempt.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void rejectingOrCancellingAnAuditRequestLeavesRowUntouched() {
        Long baseId = createCurrency("QPY");
        Long quoteId = createCurrency("QPZ");
        Long definitionId = createDefinition(baseId, quoteId, 4);
        deleteFannedOutPairs(definitionId);
        Long brandId = firstBrandId();
        ResponseEntity<Map> created = postCurrencyPair(definitionId, brandId, "AUTO", null, false);
        approve(((Number) created.getBody().get("auditRequestId")).longValue());
        Long id = findPairId(definitionId, brandId);
        createdCurrencyPairIds.add(id);

        ResponseEntity<Map> updateResponse = putCurrencyPair(id, "{\"active\": true}");
        Long auditRequestId = ((Number) updateResponse.getBody().get("auditRequestId")).longValue();

        ResponseEntity<Map> rejectResponse = reject(auditRequestId);
        assertThat(rejectResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rejectResponse.getBody().get("status")).isEqualTo("REJECTED");

        ResponseEntity<Map> afterReject = restTemplate.getForEntity(currencyPairsUrl() + "/" + id, Map.class);
        assertThat(afterReject.getBody().get("active")).isEqualTo(Boolean.FALSE);

        // A new request can be raised and cancelled without changing the row either.
        ResponseEntity<Map> secondUpdate = putCurrencyPair(id, "{\"active\": true}");
        Long secondAuditRequestId = ((Number) secondUpdate.getBody().get("auditRequestId")).longValue();

        ResponseEntity<Map> cancelResponse = cancel(secondAuditRequestId);
        assertThat(cancelResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancelResponse.getBody().get("status")).isEqualTo("CANCELLED");

        ResponseEntity<Map> afterCancel = restTemplate.getForEntity(currencyPairsUrl() + "/" + id, Map.class);
        assertThat(afterCancel.getBody().get("active")).isEqualTo(Boolean.FALSE);
    }

    @Test
    void approvalRevalidatesAgainstCurrentDataAndRejectsIfPrecisionTightened() {
        Long baseId = createCurrency("QRA");
        Long quoteId = createCurrency("QRB");
        Long definitionId = createDefinition(baseId, quoteId, 4);
        deleteFannedOutPairs(definitionId);
        Long brandId = firstBrandId();
        ResponseEntity<Map> created = postCurrencyPair(definitionId, brandId, "AUTO", null, false);
        approve(((Number) created.getBody().get("auditRequestId")).longValue());
        Long id = findPairId(definitionId, brandId);
        createdCurrencyPairIds.add(id);

        // Submitted while precision is 4: legal at submit time.
        ResponseEntity<Map> updateResponse = putCurrencyPair(id, "{\"rateType\": \"MANUAL\", \"rate\": 1.2345}");
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        Long auditRequestId = ((Number) updateResponse.getBody().get("auditRequestId")).longValue();

        // The parent definition's precision tightens to 2 before anyone approves.
        ResponseEntity<Map> precisionUpdate = restTemplate.exchange(definitionsUrl() + "/" + definitionId,
                HttpMethod.PUT, jsonEntity("{\"precision\": 2}"), Map.class);
        assertThat(precisionUpdate.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> approveResponse = approve(auditRequestId);
        assertThat(approveResponse.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(approveResponse.getBody().get("auditRequestId")).isEqualTo(auditRequestId.intValue());

        // Row left untouched.
        ResponseEntity<Map> afterFailedApprove = restTemplate.getForEntity(currencyPairsUrl() + "/" + id, Map.class);
        assertThat(afterFailedApprove.getBody().get("rateType")).isEqualTo("AUTO");
        assertThat(afterFailedApprove.getBody().get("rate")).isNull();

        // The request stays PENDING so it can be inspected/withdrawn.
        ResponseEntity<Map> detail = restTemplate.getForEntity(auditRequestsUrl() + "/" + auditRequestId, Map.class);
        assertThat(detail.getBody().get("status")).isEqualTo("PENDING");
        assertThat(detail.getBody().get("applyError")).isNotNull();
    }

    @Test
    void definitionFanOutAndDeleteCascadeWriteDirectlyWithNoAuditRequests() {
        Long baseId = createCurrency("QRC");
        Long quoteId = createCurrency("QRD");

        Integer auditCountBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_request WHERE entity_type = 'CURRENCY_PAIR'", Integer.class);

        Long definitionId = createDefinition(baseId, quoteId, 4);
        ResponseEntity<Map[]> fannedOut = restTemplate.getForEntity(
                currencyPairsUrl() + "?currencyPairDefinitionId=" + definitionId, Map[].class);
        assertThat(fannedOut.getBody()).hasSize(7);

        Integer auditCountAfterCreate = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_request WHERE entity_type = 'CURRENCY_PAIR'", Integer.class);
        assertThat(auditCountAfterCreate).isEqualTo(auditCountBefore);

        restTemplate.delete(definitionsUrl() + "/" + definitionId);
        createdDefinitionIds.remove(definitionId);

        Integer remainingPairs = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM currency_pair WHERE currency_pair_definition_id = ?", Integer.class,
                definitionId);
        assertThat(remainingPairs).isEqualTo(0);

        Integer auditCountAfterDelete = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_request WHERE entity_type = 'CURRENCY_PAIR'", Integer.class);
        assertThat(auditCountAfterDelete).isEqualTo(auditCountBefore);
    }

    // --- depositRate/withdrawalRate (入金/出金加點完成) ---

    private java.math.BigDecimal[] currentBrandSpread(Long brandId) {
        return jdbcTemplate.queryForObject(
                "SELECT deposit_spread_percent, withdrawal_spread_percent FROM brand_spread WHERE brand_id = ?",
                (rs, rowNum) -> new java.math.BigDecimal[] {
                        rs.getBigDecimal("deposit_spread_percent"), rs.getBigDecimal("withdrawal_spread_percent") },
                brandId);
    }

    /**
     * baseRate * (1 + spreadPercent / 100) — the same multiplicative markup
     * {@link com.wdd.backend.dto.CurrencyPair#getDepositRate()}/
     * {@code getWithdrawalRate()} apply.
     */
    private static java.math.BigDecimal applyPercent(String baseRate, java.math.BigDecimal spreadPercent) {
        return new java.math.BigDecimal(baseRate)
                .multiply(java.math.BigDecimal.ONE.add(spreadPercent.divide(java.math.BigDecimal.valueOf(100))));
    }

    @Test
    void depositRateAndWithdrawalRateNullForAutoPairNeverSynced() {
        Long baseId = createCurrency("QRE");
        Long quoteId = createCurrency("QRF");
        Long definitionId = createDefinition(baseId, quoteId, 4);
        Long brandId = firstBrandId();
        Long id = findPairId(definitionId, brandId);
        createdCurrencyPairIds.add(id);

        // No exchange_rate row exists yet for this fresh definition.
        ResponseEntity<Map> response = restTemplate.getForEntity(currencyPairsUrl() + "/" + id, Map.class);
        assertThat(response.getBody().get("rateType")).isEqualTo("AUTO");
        assertThat(response.getBody().get("depositRate")).isNull();
        assertThat(response.getBody().get("withdrawalRate")).isNull();
    }

    @Test
    void depositRateAndWithdrawalRateComputedForManualPairUsingBrandDefaultSpread() {
        Long baseId = createCurrency("QRG");
        Long quoteId = createCurrency("QRH");
        Long definitionId = createDefinition(baseId, quoteId, 4);
        deleteFannedOutPairs(definitionId);
        Long brandId = firstBrandId();
        ResponseEntity<Map> created = postCurrencyPair(definitionId, brandId, "MANUAL", "150.25", true);
        approve(((Number) created.getBody().get("auditRequestId")).longValue());
        Long id = findPairId(definitionId, brandId);
        createdCurrencyPairIds.add(id);

        java.math.BigDecimal[] brandSpread = currentBrandSpread(brandId);
        java.math.BigDecimal expectedDeposit = applyPercent("150.25", brandSpread[0]);
        java.math.BigDecimal expectedWithdrawal = applyPercent("150.25", brandSpread[1]);

        ResponseEntity<Map> pair = restTemplate.getForEntity(currencyPairsUrl() + "/" + id, Map.class);
        assertThat(new java.math.BigDecimal(pair.getBody().get("depositRate").toString()))
                .isEqualByComparingTo(expectedDeposit);
        assertThat(new java.math.BigDecimal(pair.getBody().get("withdrawalRate").toString()))
                .isEqualByComparingTo(expectedWithdrawal);

        // Cross-check against GET /api/spreads/effective's DEFAULT-source resolution for the same pair.
        ResponseEntity<Map[]> effective = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/spreads/effective?brandId=" + brandId, Map[].class);
        Map<?, ?> match = java.util.Arrays.stream(effective.getBody())
                .filter(m -> id.intValue() == ((Number) m.get("currencyPairId")).intValue())
                .findFirst().orElseThrow();
        assertThat(match.get("source")).isEqualTo("DEFAULT");
        assertThat(new java.math.BigDecimal(match.get("depositSpreadPercent").toString())).isEqualByComparingTo(brandSpread[0]);
    }

    @Test
    void depositRateAndWithdrawalRateComputedForAutoPairUsingLatestExchangeRate() {
        Long baseId = createCurrency("QRI");
        Long quoteId = createCurrency("QRJ");
        Long definitionId = createDefinition(baseId, quoteId, 4);
        Long brandId = firstBrandId();
        Long id = findPairId(definitionId, brandId);
        createdCurrencyPairIds.add(id);

        jdbcTemplate.update(
                "INSERT INTO exchange_rate (currency_pair_definition_id, brand_id, rate, deposit_rate, "
                        + "withdrawal_rate, rate_minute, source, updated_at) VALUES (?, ?, 149.80, 149.80, 149.80, "
                        + "NOW(), 'test-seed', NOW())",
                definitionId, brandId);

        java.math.BigDecimal[] brandSpread = currentBrandSpread(brandId);
        java.math.BigDecimal expectedDeposit = applyPercent("149.80", brandSpread[0]);
        java.math.BigDecimal expectedWithdrawal = applyPercent("149.80", brandSpread[1]);

        ResponseEntity<Map> pair = restTemplate.getForEntity(currencyPairsUrl() + "/" + id, Map.class);
        assertThat(pair.getBody().get("rateType")).isEqualTo("AUTO");
        assertThat(new java.math.BigDecimal(pair.getBody().get("depositRate").toString()))
                .isEqualByComparingTo(expectedDeposit);
        assertThat(new java.math.BigDecimal(pair.getBody().get("withdrawalRate").toString()))
                .isEqualByComparingTo(expectedWithdrawal);

        jdbcTemplate.update(
                "DELETE FROM exchange_rate WHERE currency_pair_definition_id = ? AND brand_id = ?",
                definitionId, brandId);
    }

    @Test
    void depositRateAndWithdrawalRateUseSpreadGroupSpreadWhenAssigned() {
        Long baseId = createCurrency("QRK");
        Long quoteId = createCurrency("QRL");
        Long definitionId = createDefinition(baseId, quoteId, 4);
        deleteFannedOutPairs(definitionId);
        Long brandId = firstBrandId();
        ResponseEntity<Map> created = postCurrencyPair(definitionId, brandId, "MANUAL", "100.00", true);
        approve(((Number) created.getBody().get("auditRequestId")).longValue());
        Long id = findPairId(definitionId, brandId);
        createdCurrencyPairIds.add(id);

        jdbcTemplate.update(
                "INSERT INTO spread_group (brand_id, name, deposit_spread_percent, withdrawal_spread_percent) "
                        + "VALUES (?, ?, 1.5, 2.5)",
                brandId, "QR-test-group");
        Long groupId = jdbcTemplate.queryForObject(
                "SELECT id FROM spread_group WHERE brand_id = ? AND name = ?", Long.class, brandId, "QR-test-group");
        jdbcTemplate.update("UPDATE currency_pair SET spread_group_id = ? WHERE id = ?", groupId, id);

        ResponseEntity<Map> pair = restTemplate.getForEntity(currencyPairsUrl() + "/" + id, Map.class);
        assertThat(pair.getBody().get("spreadGroupId")).isEqualTo(groupId.intValue());
        assertThat(new java.math.BigDecimal(pair.getBody().get("depositRate").toString()))
                .isEqualByComparingTo(new java.math.BigDecimal("101.50"));
        assertThat(new java.math.BigDecimal(pair.getBody().get("withdrawalRate").toString()))
                .isEqualByComparingTo(new java.math.BigDecimal("102.50"));

        // Cross-check against GET /api/spreads/effective's GROUP-source resolution for the same pair.
        ResponseEntity<Map[]> effective = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/spreads/effective?brandId=" + brandId, Map[].class);
        Map<?, ?> match = java.util.Arrays.stream(effective.getBody())
                .filter(m -> id.intValue() == ((Number) m.get("currencyPairId")).intValue())
                .findFirst().orElseThrow();
        assertThat(match.get("source")).isEqualTo("GROUP");
        assertThat(new java.math.BigDecimal(match.get("depositSpreadPercent").toString()))
                .isEqualByComparingTo(new java.math.BigDecimal("1.5"));

        jdbcTemplate.update("UPDATE currency_pair SET spread_group_id = NULL WHERE id = ?", id);
        jdbcTemplate.update("DELETE FROM spread_group WHERE id = ?", groupId);
    }

    @Test
    void createAndUpdateIgnoreDepositRateAndWithdrawalRateIfSentInBody() {
        Long baseId = createCurrency("QRM");
        Long quoteId = createCurrency("QRN");
        Long definitionId = createDefinition(baseId, quoteId, 4);
        deleteFannedOutPairs(definitionId);
        Long brandId = firstBrandId();

        String createBody = "{\"currencyPairDefinitionId\": " + definitionId + ", \"brandId\": " + brandId
                + ", \"rateType\": \"MANUAL\", \"rate\": 100.00, \"active\": true, "
                + "\"depositRate\": 999.99, \"withdrawalRate\": 999.99}";
        ResponseEntity<Map> created = restTemplate.postForEntity(currencyPairsUrl(), jsonEntity(createBody),
                Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        Long createAuditId = ((Number) created.getBody().get("auditRequestId")).longValue();
        createdAuditRequestIds.add(createAuditId);
        approve(createAuditId);
        Long id = findPairId(definitionId, brandId);
        createdCurrencyPairIds.add(id);

        java.math.BigDecimal[] brandSpread = currentBrandSpread(brandId);
        java.math.BigDecimal expectedDeposit = applyPercent("100.00", brandSpread[0]);

        ResponseEntity<Map> pair = restTemplate.getForEntity(currencyPairsUrl() + "/" + id, Map.class);
        assertThat(new java.math.BigDecimal(pair.getBody().get("depositRate").toString()))
                .isEqualByComparingTo(expectedDeposit);

        ResponseEntity<Map> updateResponse = putCurrencyPair(id,
                "{\"rate\": 200.00, \"depositRate\": 1.00, \"withdrawalRate\": 1.00}");
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        approve(((Number) updateResponse.getBody().get("auditRequestId")).longValue());

        java.math.BigDecimal expectedDepositAfterUpdate = applyPercent("200.00", brandSpread[0]);
        ResponseEntity<Map> pairAfterUpdate = restTemplate.getForEntity(currencyPairsUrl() + "/" + id, Map.class);
        assertThat(new java.math.BigDecimal(pairAfterUpdate.getBody().get("depositRate").toString()))
                .isEqualByComparingTo(expectedDepositAfterUpdate);

        // The submitted (bogus) depositRate/withdrawalRate never made it into the audit trail either.
        ResponseEntity<Map> auditDetail = restTemplate.getForEntity(auditRequestsUrl() + "/" + createAuditId,
                Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> afterData = (Map<String, Object>) auditDetail.getBody().get("afterData");
        assertThat(afterData).doesNotContainKeys("depositRate", "withdrawalRate");
    }
}
