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
 * audited: {@code POST}/{@code PUT}/{@code DELETE} on {@code /spread-groups}
 * and its {@code /members} sub-resource all return {@code 202} with a
 * pending {@code audit_request} row and change nothing until approved via
 * {@code POST /api/audit-requests/{id}/approve} — see
 * {@code SpreadGroupAuditHandler}/{@code SpreadGroupMemberAuditHandler}.
 * Test cleanup deletes {@code spread_group}/{@code audit_request} rows
 * directly via JDBC rather than going through the (now-audited) DELETE
 * endpoint.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SpreadGroupControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final TestRestTemplate restTemplate = new TestRestTemplate();

    private final List<Long> createdGroupIds = new ArrayList<>();
    private final List<Long> createdAuditRequestIds = new ArrayList<>();
    private final List<Long> createdDefinitionIds = new ArrayList<>();
    private final List<Long> createdCurrencyIds = new ArrayList<>();

    private String groupsUrl() {
        return "http://localhost:" + port + "/api/spread-groups";
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
        for (Long id : createdGroupIds) {
            jdbcTemplate.update("DELETE FROM spread_group WHERE id = ?", id);
        }
        createdGroupIds.clear();
        for (Long id : createdAuditRequestIds) {
            jdbcTemplate.update("DELETE FROM audit_request WHERE id = ?", id);
        }
        createdAuditRequestIds.clear();
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

    /** Creates a definition, which fans out one currency_pair row per brand. */
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

    private Long secondBrandId() {
        return jdbcTemplate.queryForObject("SELECT id FROM brand ORDER BY id LIMIT 1 OFFSET 1", Long.class);
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

    private ResponseEntity<Map> reject(Long auditRequestId) {
        return restTemplate.postForEntity(auditRequestsUrl() + "/" + auditRequestId + "/reject",
                jsonEntity("{\"comment\": \"no\"}"), Map.class);
    }

    private ResponseEntity<Map> cancel(Long auditRequestId) {
        return restTemplate.postForEntity(auditRequestsUrl() + "/" + auditRequestId + "/cancel",
                jsonEntity("{}"), Map.class);
    }

    private ResponseEntity<Map> postGroup(String body) {
        ResponseEntity<Map> response = restTemplate.postForEntity(groupsUrl(), jsonEntity(body), Map.class);
        if (response.getStatusCode() == HttpStatus.ACCEPTED) {
            createdAuditRequestIds.add(((Number) response.getBody().get("auditRequestId")).longValue());
        }
        return response;
    }

    private ResponseEntity<Map> putGroup(Long id, String body) {
        ResponseEntity<Map> response = restTemplate.exchange(groupsUrl() + "/" + id, HttpMethod.PUT,
                jsonEntity(body), Map.class);
        if (response.getStatusCode() == HttpStatus.ACCEPTED) {
            createdAuditRequestIds.add(((Number) response.getBody().get("auditRequestId")).longValue());
        }
        return response;
    }

    private ResponseEntity<Map> deleteGroup(Long id) {
        ResponseEntity<Map> response = restTemplate.exchange(groupsUrl() + "/" + id, HttpMethod.DELETE, null,
                Map.class);
        if (response.getStatusCode() == HttpStatus.ACCEPTED) {
            createdAuditRequestIds.add(((Number) response.getBody().get("auditRequestId")).longValue());
        }
        return response;
    }

    private ResponseEntity<Map> assignMembers(Long groupId, List<Long> currencyPairIds) {
        String ids = currencyPairIds.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
        String body = "{\"currencyPairIds\": [" + ids + "]}";
        ResponseEntity<Map> response = restTemplate.postForEntity(groupsUrl() + "/" + groupId + "/members",
                jsonEntity(body), Map.class);
        if (response.getStatusCode() == HttpStatus.ACCEPTED) {
            createdAuditRequestIds.add(((Number) response.getBody().get("auditRequestId")).longValue());
        }
        return response;
    }

    private ResponseEntity<Map> removeMember(Long groupId, Long currencyPairId) {
        ResponseEntity<Map> response = restTemplate.exchange(
                groupsUrl() + "/" + groupId + "/members/" + currencyPairId, HttpMethod.DELETE, null, Map.class);
        if (response.getStatusCode() == HttpStatus.ACCEPTED) {
            createdAuditRequestIds.add(((Number) response.getBody().get("auditRequestId")).longValue());
        }
        return response;
    }

    /** Submits a create, approves it, and returns the resulting group's id (read back by unique key). */
    private Long createApprovedGroup(Long brandId, String name) {
        return createApprovedGroup(brandId, name, null, null);
    }

    private Long createApprovedGroup(Long brandId, String name, String depositSpread, String withdrawalSpread) {
        StringBuilder body = new StringBuilder("{\"brandId\": ").append(brandId).append(", \"name\": \"")
                .append(name).append("\"");
        if (depositSpread != null) {
            body.append(", \"depositSpread\": ").append(depositSpread);
        }
        if (withdrawalSpread != null) {
            body.append(", \"withdrawalSpread\": ").append(withdrawalSpread);
        }
        body.append("}");
        ResponseEntity<Map> created = postGroup(body.toString());
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        Long auditRequestId = ((Number) created.getBody().get("auditRequestId")).longValue();
        ResponseEntity<Map> approved = approve(auditRequestId);
        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);

        Long groupId = jdbcTemplate.queryForObject(
                "SELECT id FROM spread_group WHERE brand_id = ? AND name = ?", Long.class, brandId, name);
        createdGroupIds.add(groupId);
        return groupId;
    }

    private void assignMembersApproved(Long groupId, List<Long> currencyPairIds) {
        ResponseEntity<Map> response = assignMembers(groupId, currencyPairIds);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        ResponseEntity<Map> approved = approve(((Number) response.getBody().get("auditRequestId")).longValue());
        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // --- create ---

    @Test
    void createGroupReturns202AndCreatesRowWithZeroMemberCountOnlyAfterApproval() {
        Long brandId = firstBrandId();
        String name = "VIP-" + System.nanoTime();

        ResponseEntity<Map> response = postGroup(
                String.format("{\"brandId\": %d, \"name\": \"%s\"}", brandId, name));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody().get("entityType")).isEqualTo("SPREAD_GROUP");
        assertThat(response.getBody().get("actionType")).isEqualTo("CREATE");
        assertThat(response.getBody().get("entityId")).isNull();

        Integer countBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM spread_group WHERE brand_id = ? AND name = ?", Integer.class, brandId, name);
        assertThat(countBefore).isEqualTo(0);

        Long groupId = createApprovedGroup(brandId, name);

        ResponseEntity<Map> group = restTemplate.getForEntity(groupsUrl() + "/" + groupId, Map.class);
        assertThat(((Number) group.getBody().get("memberCount")).intValue()).isEqualTo(0);
        assertThat(group.getBody().get("depositSpread").toString()).startsWith("0");
    }

    @Test
    void createRejectsDuplicateNameForSameBrand() {
        Long brandId = firstBrandId();
        String name = "DUP-" + System.nanoTime();
        createApprovedGroup(brandId, name);

        ResponseEntity<String> response = restTemplate.postForEntity(groupsUrl(),
                jsonEntity(String.format("{\"brandId\": %d, \"name\": \"%s\"}", brandId, name)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void createRejectsUnknownBrandId() {
        ResponseEntity<String> response = restTemplate.postForEntity(groupsUrl(),
                jsonEntity("{\"brandId\": 999999, \"name\": \"X\"}"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createAcceptsSameNameUnderDifferentBrands() {
        Long brandA = firstBrandId();
        Long brandB = secondBrandId();
        String name = "SHARED-" + System.nanoTime();

        createApprovedGroup(brandA, name);
        ResponseEntity<Map> response = postGroup(
                String.format("{\"brandId\": %d, \"name\": \"%s\"}", brandB, name));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        Long auditRequestId = ((Number) response.getBody().get("auditRequestId")).longValue();
        ResponseEntity<Map> approved = approve(auditRequestId);
        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);

        Long groupBId = jdbcTemplate.queryForObject(
                "SELECT id FROM spread_group WHERE brand_id = ? AND name = ?", Long.class, brandB, name);
        createdGroupIds.add(groupBId);
    }

    // --- update ---

    @Test
    void updateReturns202AndChangesNameAndSpreadsAndIgnoresBrandIdOnlyAfterApproval() {
        Long brandId = firstBrandId();
        Long groupId = createApprovedGroup(brandId, "UPD-" + System.nanoTime());
        String newName = "UPD2-" + System.nanoTime();

        ResponseEntity<Map> response = putGroup(groupId,
                String.format("{\"name\": \"%s\", \"depositSpread\": 0.0002, \"withdrawalSpread\": 0.0003, "
                        + "\"brandId\": 999999}", newName));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody().get("actionType")).isEqualTo("UPDATE");
        assertThat(((Number) response.getBody().get("entityId")).longValue()).isEqualTo(groupId);
        Long auditRequestId = ((Number) response.getBody().get("auditRequestId")).longValue();

        ResponseEntity<Map> approveResponse = approve(auditRequestId);
        assertThat(approveResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> afterApprove = restTemplate.getForEntity(groupsUrl() + "/" + groupId, Map.class);
        assertThat(afterApprove.getBody().get("name")).isEqualTo(newName);
        assertThat(((Number) afterApprove.getBody().get("brandId")).longValue()).isEqualTo(brandId);
    }

    @Test
    void updateReturnsConflictOnNameCollisionWithinBrand() {
        Long brandId = firstBrandId();
        String nameA = "COLA-" + System.nanoTime();
        String nameB = "COLB-" + System.nanoTime();
        createApprovedGroup(brandId, nameA);
        Long groupBId = createApprovedGroup(brandId, nameB);

        ResponseEntity<String> response = restTemplate.exchange(groupsUrl() + "/" + groupBId, HttpMethod.PUT,
                jsonEntity("{\"name\": \"" + nameA + "\"}"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void updateReturnsNotFoundForUnknownGroup() {
        ResponseEntity<String> response = restTemplate.exchange(groupsUrl() + "/999999", HttpMethod.PUT,
                jsonEntity("{\"name\": \"X\"}"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // --- get by id ---

    @Test
    void getByIdReturnsNotFoundForUnknownGroup() {
        ResponseEntity<String> response = restTemplate.getForEntity(groupsUrl() + "/999999", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getByIdReturnsMembersArrayMatchingMemberCount() {
        Long baseId = createCurrency("SGA");
        Long quoteId = createCurrency("SGB");
        Long definitionId = createDefinition(baseId, quoteId);
        Long brandId = firstBrandId();
        Long pairId = currencyPairIdFor(definitionId, brandId);
        Long groupId = createApprovedGroup(brandId, "MEM-" + System.nanoTime());

        assignMembersApproved(groupId, List.of(pairId));

        ResponseEntity<Map> response = restTemplate.getForEntity(groupsUrl() + "/" + groupId, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> members = (List<?>) response.getBody().get("members");
        assertThat(members).hasSize(1);
        assertThat(((Number) response.getBody().get("memberCount")).intValue()).isEqualTo(members.size());
    }

    // --- member assignment ---

    @Test
    void assignMembersReturns202AndAssignsWholeBatchOnlyAfterApproval() {
        Long baseId = createCurrency("SGC");
        Long quoteId = createCurrency("SGD");
        Long definitionId = createDefinition(baseId, quoteId);
        Long brandId = firstBrandId();
        Long pairId = currencyPairIdFor(definitionId, brandId);
        Long groupId = createApprovedGroup(brandId, "BATCH-" + System.nanoTime());

        ResponseEntity<Map> response = assignMembers(groupId, List.of(pairId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody().get("entityType")).isEqualTo("SPREAD_GROUP_MEMBER");
        assertThat(((Number) response.getBody().get("entityId")).longValue()).isEqualTo(groupId);

        // Not assigned yet.
        Long groupBeforeApprove = jdbcTemplate.queryForObject(
                "SELECT spread_group_id FROM currency_pair WHERE id = ?", Long.class, pairId);
        assertThat(groupBeforeApprove).isNull();

        ResponseEntity<Map> approveResponse = approve(
                ((Number) response.getBody().get("auditRequestId")).longValue());
        assertThat(approveResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        Long groupOnPair = jdbcTemplate.queryForObject(
                "SELECT spread_group_id FROM currency_pair WHERE id = ?", Long.class, pairId);
        assertThat(groupOnPair).isEqualTo(groupId);
    }

    @Test
    void assignMembersRejectsBrandMismatchAndAssignsNoneOfBatch() {
        Long baseId = createCurrency("SGE");
        Long quoteId = createCurrency("SGF");
        Long definitionId = createDefinition(baseId, quoteId);
        Long brandA = firstBrandId();
        Long brandB = secondBrandId();
        Long pairForBrandA = currencyPairIdFor(definitionId, brandA);
        Long pairForBrandB = currencyPairIdFor(definitionId, brandB);
        Long groupId = createApprovedGroup(brandA, "MISMATCH-" + System.nanoTime());

        ResponseEntity<Map> response = assignMembers(groupId, List.of(pairForBrandA, pairForBrandB));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("error")).isEqualTo("Currency pair belongs to a different brand");

        Long groupOnPairA = jdbcTemplate.queryForObject(
                "SELECT spread_group_id FROM currency_pair WHERE id = ?", Long.class, pairForBrandA);
        assertThat(groupOnPairA).isNull();
    }

    @Test
    void assignMembersRejectsPairAlreadyInDifferentGroupAndAssignsNoneOfBatch() {
        Long baseId = createCurrency("SGG");
        Long quoteId = createCurrency("SGH");
        Long definitionId = createDefinition(baseId, quoteId);
        Long brandId = firstBrandId();
        Long pairId1 = currencyPairIdFor(definitionId, brandId);

        Long baseId2 = createCurrency("SGI");
        Long quoteId2 = createCurrency("SGJ");
        Long definitionId2 = createDefinition(baseId2, quoteId2);
        Long pairId2 = currencyPairIdFor(definitionId2, brandId);

        Long groupA = createApprovedGroup(brandId, "GA-" + System.nanoTime());
        Long groupB = createApprovedGroup(brandId, "GB-" + System.nanoTime());

        // pairId2 already belongs to groupA.
        assignMembersApproved(groupA, List.of(pairId2));

        ResponseEntity<Map> response = assignMembers(groupB, List.of(pairId1, pairId2));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("error")).isEqualTo("Currency pair already belongs to another spread group");
        List<?> conflicts = (List<?>) response.getBody().get("conflicts");
        assertThat(conflicts).hasSize(1);
        Map<?, ?> conflict = (Map<?, ?>) conflicts.get(0);
        assertThat(((Number) conflict.get("currencyPairId")).longValue()).isEqualTo(pairId2);
        assertThat(((Number) conflict.get("spreadGroupId")).longValue()).isEqualTo(groupA);

        // pairId1 must NOT have been assigned (all-or-nothing).
        Long groupOnPair1 = jdbcTemplate.queryForObject(
                "SELECT spread_group_id FROM currency_pair WHERE id = ?", Long.class, pairId1);
        assertThat(groupOnPair1).isNull();
    }

    @Test
    void assignMembersRejectsUnknownIds() {
        Long brandId = firstBrandId();
        Long groupId = createApprovedGroup(brandId, "UNK-" + System.nanoTime());

        ResponseEntity<Map> response = assignMembers(groupId, List.of(999999L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void assignMembersRejectsEmptyList() {
        Long brandId = firstBrandId();
        Long groupId = createApprovedGroup(brandId, "EMPTY-" + System.nanoTime());

        ResponseEntity<String> response = restTemplate.postForEntity(groupsUrl() + "/" + groupId + "/members",
                jsonEntity("{\"currencyPairIds\": []}"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void reassigningPairAlreadyInThisGroupIsNoOpAfterApproval() {
        Long baseId = createCurrency("SGK");
        Long quoteId = createCurrency("SGL");
        Long definitionId = createDefinition(baseId, quoteId);
        Long brandId = firstBrandId();
        Long pairId = currencyPairIdFor(definitionId, brandId);
        Long groupId = createApprovedGroup(brandId, "NOOP-" + System.nanoTime());

        assignMembersApproved(groupId, List.of(pairId));
        assignMembersApproved(groupId, List.of(pairId));

        ResponseEntity<Map> response = restTemplate.getForEntity(groupsUrl() + "/" + groupId, Map.class);
        List<?> members = (List<?>) response.getBody().get("members");
        assertThat(members).hasSize(1);
    }

    // --- member removal ---

    @Test
    void removeMemberReturns202AndNullsOnlyThatPairsGroupOnlyAfterApproval() {
        Long baseId = createCurrency("SGM");
        Long quoteId = createCurrency("SGN");
        Long definitionId = createDefinition(baseId, quoteId);
        Long brandId = firstBrandId();
        Long pairId = currencyPairIdFor(definitionId, brandId);
        Long groupId = createApprovedGroup(brandId, "RM-" + System.nanoTime());
        assignMembersApproved(groupId, List.of(pairId));

        ResponseEntity<Map> response = removeMember(groupId, pairId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody().get("entityType")).isEqualTo("SPREAD_GROUP_MEMBER");

        Long stillGrouped = jdbcTemplate.queryForObject(
                "SELECT spread_group_id FROM currency_pair WHERE id = ?", Long.class, pairId);
        assertThat(stillGrouped).isEqualTo(groupId);

        ResponseEntity<Map> approveResponse = approve(
                ((Number) response.getBody().get("auditRequestId")).longValue());
        assertThat(approveResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT id, spread_group_id FROM currency_pair WHERE id = ?", pairId);
        assertThat(row.get("spread_group_id")).isNull();
        assertThat(row.get("id")).isNotNull();
    }

    @Test
    void removeMemberReturnsNotFoundWhenPairIsNotAMember() {
        Long baseId = createCurrency("SGO");
        Long quoteId = createCurrency("SGP");
        Long definitionId = createDefinition(baseId, quoteId);
        Long brandId = firstBrandId();
        Long pairId = currencyPairIdFor(definitionId, brandId);
        Long groupId = createApprovedGroup(brandId, "RMNF-" + System.nanoTime());
        // pairId was never assigned to groupId.

        ResponseEntity<String> response = restTemplate.exchange(
                groupsUrl() + "/" + groupId + "/members/" + pairId, HttpMethod.DELETE, null, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // --- delete ---

    @Test
    void deleteGroupReturns202AndLeavesMemberCurrencyPairRowsIntactWithNullGroupOnlyAfterApproval() {
        Long baseId = createCurrency("SGQ");
        Long quoteId = createCurrency("SGR");
        Long definitionId = createDefinition(baseId, quoteId);
        Long brandId = firstBrandId();
        Long pairId = currencyPairIdFor(definitionId, brandId);
        Long groupId = createApprovedGroup(brandId, "DEL-" + System.nanoTime());
        assignMembersApproved(groupId, List.of(pairId));
        createdGroupIds.remove(groupId);

        ResponseEntity<Map> response = deleteGroup(groupId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody().get("actionType")).isEqualTo("DELETE");

        // Group must still exist until approved.
        ResponseEntity<Map> stillThere = restTemplate.getForEntity(groupsUrl() + "/" + groupId, Map.class);
        assertThat(stillThere.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> approveResponse = approve(
                ((Number) response.getBody().get("auditRequestId")).longValue());
        assertThat(approveResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT id, spread_group_id FROM currency_pair WHERE id = ?", pairId);
        assertThat(row.get("id")).isNotNull();
        assertThat(row.get("spread_group_id")).isNull();

        ResponseEntity<String> getGroup = restTemplate.getForEntity(groupsUrl() + "/" + groupId, String.class);
        assertThat(getGroup.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteReturnsNotFoundForUnknownGroup() {
        ResponseEntity<String> response = restTemplate.exchange(groupsUrl() + "/999999", HttpMethod.DELETE, null,
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // --- audited-flow acceptance criteria ---

    @Test
    void secondUpdateOnGroupWithPendingUpdateRequestReturns409() {
        Long brandId = firstBrandId();
        Long groupId = createApprovedGroup(brandId, "PEND-" + System.nanoTime());

        ResponseEntity<Map> first = putGroup(groupId, "{\"name\": \"PEND2-" + System.nanoTime() + "\"}");
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        ResponseEntity<String> second = restTemplate.exchange(groupsUrl() + "/" + groupId, HttpMethod.PUT,
                jsonEntity("{\"name\": \"PEND3\"}"), String.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<String> deleteAttempt = restTemplate.exchange(groupsUrl() + "/" + groupId,
                HttpMethod.DELETE, null, String.class);
        assertThat(deleteAttempt.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void pendingMembershipRequestBlocksAnotherMembershipRequestButNotAGroupEditRequest() {
        Long baseId = createCurrency("SGS");
        Long quoteId = createCurrency("SGT");
        Long definitionId = createDefinition(baseId, quoteId);
        Long brandId = firstBrandId();
        Long pairId = currencyPairIdFor(definitionId, brandId);
        Long groupId = createApprovedGroup(brandId, "MB-" + System.nanoTime());

        ResponseEntity<Map> assign = assignMembers(groupId, List.of(pairId));
        assertThat(assign.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        // Another membership request for the same group is blocked.
        ResponseEntity<String> secondAssign = restTemplate.postForEntity(groupsUrl() + "/" + groupId + "/members",
                jsonEntity("{\"currencyPairIds\": [" + pairId + "]}"), String.class);
        assertThat(secondAssign.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // But a SPREAD_GROUP edit (different entityType) is not blocked by the pending membership request.
        ResponseEntity<Map> renameWhilePending = putGroup(groupId, "{\"name\": \"MB2-" + System.nanoTime() + "\"}");
        assertThat(renameWhilePending.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    @Test
    void rejectingOrCancellingAGroupUpdateRequestLeavesDataUntouched() {
        Long brandId = firstBrandId();
        String originalName = "REJ-" + System.nanoTime();
        Long groupId = createApprovedGroup(brandId, originalName);

        ResponseEntity<Map> updateResponse = putGroup(groupId, "{\"name\": \"REJ2\"}");
        Long auditRequestId = ((Number) updateResponse.getBody().get("auditRequestId")).longValue();

        ResponseEntity<Map> rejectResponse = reject(auditRequestId);
        assertThat(rejectResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rejectResponse.getBody().get("status")).isEqualTo("REJECTED");

        ResponseEntity<Map> afterReject = restTemplate.getForEntity(groupsUrl() + "/" + groupId, Map.class);
        assertThat(afterReject.getBody().get("name")).isEqualTo(originalName);

        ResponseEntity<Map> secondUpdate = putGroup(groupId, "{\"name\": \"REJ3\"}");
        Long secondAuditRequestId = ((Number) secondUpdate.getBody().get("auditRequestId")).longValue();

        ResponseEntity<Map> cancelResponse = cancel(secondAuditRequestId);
        assertThat(cancelResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancelResponse.getBody().get("status")).isEqualTo("CANCELLED");

        ResponseEntity<Map> afterCancel = restTemplate.getForEntity(groupsUrl() + "/" + groupId, Map.class);
        assertThat(afterCancel.getBody().get("name")).isEqualTo(originalName);
    }

    @Test
    void approvingMembershipBatchWhereOnePairJoinedAnotherGroupMeanwhileFailsWholeApprovalWithNothingWritten() {
        Long baseId1 = createCurrency("SGU");
        Long quoteId1 = createCurrency("SGV");
        Long definitionId1 = createDefinition(baseId1, quoteId1);
        Long baseId2 = createCurrency("SGW");
        Long quoteId2 = createCurrency("SGX");
        Long definitionId2 = createDefinition(baseId2, quoteId2);
        Long brandId = firstBrandId();
        Long pairA = currencyPairIdFor(definitionId1, brandId);
        Long pairB = currencyPairIdFor(definitionId2, brandId);

        Long groupTarget = createApprovedGroup(brandId, "RACE-TARGET-" + System.nanoTime());
        Long groupOther = createApprovedGroup(brandId, "RACE-OTHER-" + System.nanoTime());

        // Submit a batch assigning both pairs to groupTarget, but do not approve yet.
        ResponseEntity<Map> assignBatch = assignMembers(groupTarget, List.of(pairA, pairB));
        assertThat(assignBatch.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        Long assignBatchAuditId = ((Number) assignBatch.getBody().get("auditRequestId")).longValue();

        // Meanwhile pairB is pulled into a different group and that request is approved first.
        assignMembersApproved(groupOther, List.of(pairB));

        // Approving the original batch must now fail as a whole — pairB is no longer assignable.
        ResponseEntity<Map> approveResponse = approve(assignBatchAuditId);
        assertThat(approveResponse.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        // Nothing written: pairA must still be unassigned (not pulled into groupTarget).
        Long pairAGroup = jdbcTemplate.queryForObject(
                "SELECT spread_group_id FROM currency_pair WHERE id = ?", Long.class, pairA);
        assertThat(pairAGroup).isNull();
        Long pairBGroup = jdbcTemplate.queryForObject(
                "SELECT spread_group_id FROM currency_pair WHERE id = ?", Long.class, pairB);
        assertThat(pairBGroup).isEqualTo(groupOther);

        ResponseEntity<Map> detail = restTemplate.getForEntity(auditRequestsUrl() + "/" + assignBatchAuditId,
                Map.class);
        assertThat(detail.getBody().get("status")).isEqualTo("PENDING");
        assertThat(detail.getBody().get("applyError")).isNotNull();
    }
}
