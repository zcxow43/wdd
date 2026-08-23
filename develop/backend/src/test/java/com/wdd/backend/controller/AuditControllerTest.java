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

import com.wdd.backend.dto.AuditRequest;
import com.wdd.backend.mapper.AuditRequestMapper;
import com.wdd.backend.service.AuditService;
import com.wdd.backend.service.StubAuditHandler;

/**
 * Integration tests against the real DB (see class-level docs on sibling
 * controller tests for the established pattern). {@code submit} is not an
 * HTTP endpoint, so test data is seeded by calling {@link AuditService}
 * directly; everything under test here is the HTTP surface:
 * list/detail/approve/reject/cancel. Approve is exercised end-to-end
 * against {@link StubAuditHandler}, the only registered handler in this
 * module (real entities register their own handlers in their own specs).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuditControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuditService auditService;

    @Autowired
    private AuditRequestMapper auditRequestMapper;

    private final TestRestTemplate restTemplate = new TestRestTemplate();

    private final List<Long> createdIds = new ArrayList<>();

    private String baseUrl() {
        return "http://localhost:" + port + "/api/audit-requests";
    }

    @AfterEach
    void cleanUp() {
        for (Long id : createdIds) {
            jdbcTemplate.update("DELETE FROM audit_request WHERE id = ?", id);
        }
        createdIds.clear();
        StubAuditHandler.TARGET_STATE.clear();
    }

    private HttpEntity<String> jsonEntity(String body, String actor) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (actor != null) {
            headers.set("X-Actor", actor);
        }
        return new HttpEntity<>(body, headers);
    }

    private Long submit(String entityType, Long entityId, Long brandId, String summary, Object afterData) {
        AuditRequest created = auditService.submit(entityType, "UPDATE", entityId, brandId, summary,
                Map.of("value", "old"), afterData, "alice");
        createdIds.add(created.getId());
        return created.getId();
    }

    private long uniqueEntityId() {
        return System.nanoTime();
    }

    // --- list ---

    @Test
    void listReturnsNewestFirstAndNarrowsByFilters() {
        long e1 = uniqueEntityId();
        long e2 = uniqueEntityId();
        Long id1 = submit("TEST_STUB", e1, 1L, "first change", Map.of("value", "a"));
        Long id2 = submit("TEST_STUB", e2, 2L, "second change", Map.of("value", "b"));

        ResponseEntity<List> all = restTemplate.getForEntity(baseUrl(), List.class);
        assertThat(all.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> body = all.getBody();
        int idx1 = indexOfId(body, id1);
        int idx2 = indexOfId(body, id2);
        assertThat(idx2).isLessThan(idx1); // id2 requested after id1 -> newest first

        ResponseEntity<List> byBrand = restTemplate.getForEntity(baseUrl() + "?brandId=2", List.class);
        assertThat(byBrand.getBody().stream().anyMatch(m -> idOf((Map<?, ?>) m) == id2)).isTrue();
        assertThat(byBrand.getBody().stream().anyMatch(m -> idOf((Map<?, ?>) m) == id1)).isFalse();

        ResponseEntity<List> byEntityId = restTemplate.getForEntity(baseUrl() + "?entityId=" + e1, List.class);
        assertThat(byEntityId.getBody()).hasSize(1);
        assertThat(idOf((Map<?, ?>) byEntityId.getBody().get(0))).isEqualTo(id1);

        ResponseEntity<List> byEntityType = restTemplate.getForEntity(baseUrl() + "?entityType=TEST_STUB",
                List.class);
        assertThat(byEntityType.getBody().stream().anyMatch(m -> idOf((Map<?, ?>) m) == id1)).isTrue();

        ResponseEntity<List> byStatus = restTemplate.getForEntity(baseUrl() + "?status=PENDING", List.class);
        assertThat(byStatus.getBody().stream().anyMatch(m -> idOf((Map<?, ?>) m) == id1)).isTrue();
    }

    @Test
    void listResponseOmitsBeforeAndAfterData() {
        Long id = submit("TEST_STUB", uniqueEntityId(), 1L, "omit test", Map.of("value", "a"));

        ResponseEntity<List> response = restTemplate.getForEntity(baseUrl(), List.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> item = (Map<String, Object>) response.getBody().stream()
                .filter(m -> idOf((Map<?, ?>) m) == id)
                .findFirst().orElseThrow();

        assertThat(item).doesNotContainKeys("beforeData", "afterData");
    }

    private static int indexOfId(List<Map<String, Object>> body, Long id) {
        for (int i = 0; i < body.size(); i++) {
            if (idOf(body.get(i)) == id) {
                return i;
            }
        }
        throw new AssertionError("id not found: " + id);
    }

    private static long idOf(Map<?, ?> m) {
        return ((Number) m.get("id")).longValue();
    }

    // --- get by id ---

    @Test
    void getByIdReturnsFullDetailIncludingBeforeAndAfterData() {
        Long id = submit("TEST_STUB", uniqueEntityId(), 1L, "detail test", Map.of("value", "new"));

        ResponseEntity<Map> response = restTemplate.getForEntity(baseUrl() + "/" + id, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKeys("beforeData", "afterData");
        assertThat(((Map<?, ?>) response.getBody().get("beforeData")).get("value")).isEqualTo("old");
        assertThat(((Map<?, ?>) response.getBody().get("afterData")).get("value")).isEqualTo("new");
    }

    @Test
    void getByIdReturnsNotFoundForUnknownId() {
        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl() + "/99999999", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // --- one-pending-per-target ---

    @Test
    void secondPendingSubmitForSameTargetConflicts() {
        long entityId = uniqueEntityId();
        submit("TEST_STUB", entityId, 1L, "first", Map.of("value", "a"));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> auditService.submit("TEST_STUB", "UPDATE", entityId, 1L, "second", Map.of("value", "old"),
                        Map.of("value", "b"), "bob"))
                .isInstanceOf(com.wdd.backend.exception.AuditRequestConflictException.class);
    }

    @Test
    void databaseUniqueIndexRejectsSecondPendingRowEvenIfServiceCheckIsBypassed() {
        long entityId = uniqueEntityId();
        AuditRequest first = new AuditRequest();
        first.setEntityType("TEST_STUB");
        first.setActionType("UPDATE");
        first.setEntityId(entityId);
        first.setBrandId(1L);
        first.setSummary("bypass check 1");
        first.setStatus("PENDING");
        first.setRequestedBy("alice");
        auditRequestMapper.insert(first);
        createdIds.add(first.getId());

        AuditRequest second = new AuditRequest();
        second.setEntityType("TEST_STUB");
        second.setActionType("UPDATE");
        second.setEntityId(entityId);
        second.setBrandId(1L);
        second.setSummary("bypass check 2");
        second.setStatus("PENDING");
        second.setRequestedBy("bob");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> auditRequestMapper.insert(second))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void secondPendingSubmitAcceptedAfterFirstIsResolved() {
        long entityId = uniqueEntityId();
        Long id1 = submit("TEST_STUB", entityId, 1L, "first", Map.of("value", "a"));

        ResponseEntity<Map> cancelResponse = restTemplate.postForEntity(baseUrl() + "/" + id1 + "/cancel",
                jsonEntity("{}", "alice"), Map.class);
        assertThat(cancelResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        Long id2 = submit("TEST_STUB", entityId, 1L, "second", Map.of("value", "b"));
        assertThat(id2).isNotNull();
    }

    // --- approve ---

    @Test
    void approveRunsHandlerAndAppliesChangeToTarget() {
        long entityId = uniqueEntityId();
        Long id = submit("TEST_STUB", entityId, 1L, "approve me", Map.of("value", "new"));

        ResponseEntity<Map> response = restTemplate.postForEntity(baseUrl() + "/" + id + "/approve",
                jsonEntity("{\"comment\": \"looks fine\"}", "reviewer1"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("status")).isEqualTo("APPROVED");
        assertThat(response.getBody().get("reviewedBy")).isEqualTo("reviewer1");
        assertThat(response.getBody().get("reviewComment")).isEqualTo("looks fine");
        assertThat(response.getBody().get("reviewedAt")).isNotNull();

        assertThat(StubAuditHandler.TARGET_STATE.get(entityId)).isEqualTo(Map.of("value", "new"));

        String statusInDb = jdbcTemplate.queryForObject("SELECT status FROM audit_request WHERE id = ?",
                String.class, id);
        assertThat(statusInDb).isEqualTo("APPROVED");
    }

    @Test
    void approveOn422PathLeavesRequestPendingWithApplyErrorAndTargetUntouched() {
        long entityId = uniqueEntityId();
        Long id = submit("TEST_STUB", entityId, 1L, "will drift", Map.of("forceFail", true));

        ResponseEntity<Map> response = restTemplate.postForEntity(baseUrl() + "/" + id + "/approve",
                jsonEntity("{}", "reviewer1"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().get("auditRequestId")).isEqualTo(id.intValue());
        assertThat(response.getBody().get("error")).isNotNull();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, apply_error FROM audit_request WHERE id = ?", id);
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(row.get("apply_error")).isNotNull();

        assertThat(StubAuditHandler.TARGET_STATE).doesNotContainKey(entityId);
    }

    @Test
    void approveRejectCancelOnAlreadyResolvedRequestReturns409() {
        long entityId = uniqueEntityId();
        Long id = submit("TEST_STUB", entityId, 1L, "resolve me", Map.of("value", "new"));
        restTemplate.postForEntity(baseUrl() + "/" + id + "/approve", jsonEntity("{}", "reviewer1"), Map.class);

        ResponseEntity<String> approveAgain = restTemplate.postForEntity(baseUrl() + "/" + id + "/approve",
                jsonEntity("{}", "reviewer1"), String.class);
        assertThat(approveAgain.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<String> rejectAfter = restTemplate.postForEntity(baseUrl() + "/" + id + "/reject",
                jsonEntity("{\"comment\": \"no\"}", "reviewer1"), String.class);
        assertThat(rejectAfter.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<String> cancelAfter = restTemplate.postForEntity(baseUrl() + "/" + id + "/cancel",
                jsonEntity("{}", "reviewer1"), String.class);
        assertThat(cancelAfter.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // --- reject ---

    @Test
    void rejectWithBlankCommentReturns400() {
        Long id = submit("TEST_STUB", uniqueEntityId(), 1L, "reject me", Map.of("value", "new"));

        ResponseEntity<String> missing = restTemplate.exchange(baseUrl() + "/" + id + "/reject", HttpMethod.POST,
                jsonEntity("{}", "reviewer1"), String.class);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<String> blank = restTemplate.exchange(baseUrl() + "/" + id + "/reject", HttpMethod.POST,
                jsonEntity("{\"comment\": \"   \"}", "reviewer1"), String.class);
        assertThat(blank.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        String statusInDb = jdbcTemplate.queryForObject("SELECT status FROM audit_request WHERE id = ?",
                String.class, id);
        assertThat(statusInDb).isEqualTo("PENDING");
    }

    @Test
    void rejectWithCommentMarksRejectedAndChangesNothingOnTarget() {
        long entityId = uniqueEntityId();
        Long id = submit("TEST_STUB", entityId, 1L, "reject me too", Map.of("value", "new"));

        ResponseEntity<Map> response = restTemplate.postForEntity(baseUrl() + "/" + id + "/reject",
                jsonEntity("{\"comment\": \"not needed\"}", "reviewer1"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("status")).isEqualTo("REJECTED");
        assertThat(StubAuditHandler.TARGET_STATE).doesNotContainKey(entityId);
    }

    // --- cancel ---

    @Test
    void cancelMarksCancelledAndChangesNothingOnTarget() {
        long entityId = uniqueEntityId();
        Long id = submit("TEST_STUB", entityId, 1L, "cancel me", Map.of("value", "new"));

        ResponseEntity<Map> response = restTemplate.postForEntity(baseUrl() + "/" + id + "/cancel",
                jsonEntity("{}", "reviewer1"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("status")).isEqualTo("CANCELLED");
        assertThat(StubAuditHandler.TARGET_STATE).doesNotContainKey(entityId);
    }

    // --- actor ---

    @Test
    void xActorDefaultsToSystemWhenHeaderAbsent() {
        Long id = submit("TEST_STUB", uniqueEntityId(), 1L, "no actor header", Map.of("value", "new"));

        ResponseEntity<Map> response = restTemplate.postForEntity(baseUrl() + "/" + id + "/cancel",
                jsonEntity("{}", null), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("reviewedBy")).isEqualTo("system");
    }
}
