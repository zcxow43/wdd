package com.wdd.backend.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Integration tests for the generic {@code /api/audit-requests} API, driven entirely through
 * {@link TestAuditHandler} — a test-only fake registered as a real Spring bean — proving the
 * audit module works without any real consumer (e.g. {@code CurrencyPairAuditHandler}) wired in.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuditControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuditService auditService;

    @Autowired
    private TestAuditHandler testAuditHandler;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM audit_request");
        testAuditHandler.reset();
    }

    private Long submitUpdate(Long entityId, Map<String, Object> after) {
        return auditService.submit(TestAuditHandler.ENTITY_TYPE, AuditActionType.UPDATE, entityId, after, "Alice").getId();
    }

    private Long submitCreate(Map<String, Object> after) {
        return auditService.submit(TestAuditHandler.ENTITY_TYPE, AuditActionType.CREATE, null, after, "Alice").getId();
    }

    private Long submitDelete(Long entityId) {
        return auditService.submit(TestAuditHandler.ENTITY_TYPE, AuditActionType.DELETE, entityId, null, "Alice").getId();
    }

    // ---------- list / getById ----------

    @Test
    void list_returnsSubmittedRequestsFilteredByEntityTypeAndStatus() throws Exception {
        Long entityId = testAuditHandler.seed(Map.of("name", "Original"));
        submitUpdate(entityId, Map.of("name", "Updated"));

        mockMvc.perform(get("/api/audit-requests")
                        .param("entityType", TestAuditHandler.ENTITY_TYPE)
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$[0].entityType").value(TestAuditHandler.ENTITY_TYPE))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void list_returnsEmptyForUnknownEntityType() throws Exception {
        mockMvc.perform(get("/api/audit-requests").param("entityType", "NO_SUCH_TYPE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getById_returnsRequestWithBeforeAndAfterSnapshots() throws Exception {
        Long entityId = testAuditHandler.seed(Map.of("name", "Original"));
        Long id = submitUpdate(entityId, Map.of("name", "Updated"));

        mockMvc.perform(get("/api/audit-requests/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.actionType").value("UPDATE"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.before.name").value("Original"))
                .andExpect(jsonPath("$.after.name").value("Updated"));
    }

    @Test
    void getById_returns404WhenMissing() throws Exception {
        mockMvc.perform(get("/api/audit-requests/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Audit request not found"))
                .andExpect(jsonPath("$.id").value(999999));
    }

    // ---------- approve ----------

    @Test
    void approve_onPendingUpdate_appliesChangeAndReturns200() throws Exception {
        Long entityId = testAuditHandler.seed(Map.of("name", "Original"));
        Long id = submitUpdate(entityId, Map.of("name", "Updated"));

        mockMvc.perform(post("/api/audit-requests/" + id + "/approve")
                        .contentType("application/json")
                        .content("{\"reviewedBy\":\"Bob\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.reviewedBy").value("Bob"))
                .andExpect(jsonPath("$.reviewedAt").exists())
                .andExpect(jsonPath("$.entityId").value(entityId));

        assertThat(testAuditHandler.get(entityId)).containsEntry("name", "Updated");
    }

    @Test
    void approve_onPendingCreate_setsEntityIdFromApplyResult() throws Exception {
        Long id = submitCreate(Map.of("name", "Brand New"));

        mockMvc.perform(post("/api/audit-requests/" + id + "/approve")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.entityId").exists());
    }

    @Test
    void approve_onPendingCreate_worksWithoutRequestBody() throws Exception {
        Long id = submitCreate(Map.of("name", "No Body"));

        mockMvc.perform(post("/api/audit-requests/" + id + "/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void approve_onPendingDelete_removesEntity() throws Exception {
        Long entityId = testAuditHandler.seed(Map.of("name", "ToDelete"));
        Long id = submitDelete(entityId);

        mockMvc.perform(post("/api/audit-requests/" + id + "/approve")
                        .contentType("application/json")
                        .content("{\"reviewedBy\":\"Bob\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        assertThat(testAuditHandler.exists(entityId)).isFalse();
    }

    @Test
    void approve_returns404WhenMissing() throws Exception {
        mockMvc.perform(post("/api/audit-requests/999999/approve")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void approve_returns409WhenAlreadyApproved() throws Exception {
        Long entityId = testAuditHandler.seed(Map.of("name", "Original"));
        Long id = submitUpdate(entityId, Map.of("name", "Updated"));

        mockMvc.perform(post("/api/audit-requests/" + id + "/approve").contentType("application/json").content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/audit-requests/" + id + "/approve").contentType("application/json").content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Audit request has already been reviewed"))
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void approve_returns409AndLeavesRequestPending_whenRevalidationDetectsDrift() throws Exception {
        Long id = submitCreate(Map.of("name", "Widget"));

        // Simulate state drifting between submission and approval: another "Widget"-named
        // entity now exists live, so the handler's re-validation at approve time must fail
        // even though it passed at submission time.
        testAuditHandler.seed(Map.of("name", "Widget"));

        mockMvc.perform(post("/api/audit-requests/" + id + "/approve").contentType("application/json").content("{}"))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/audit-requests/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    // ---------- reject ----------

    @Test
    void reject_onPendingRequest_marksRejectedAndReturns200() throws Exception {
        Long entityId = testAuditHandler.seed(Map.of("name", "Original"));
        Long id = submitUpdate(entityId, Map.of("name", "Updated"));

        mockMvc.perform(post("/api/audit-requests/" + id + "/reject")
                        .contentType("application/json")
                        .content("{\"reviewedBy\":\"Bob\",\"rejectReason\":\"匯率過高，請重新確認\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.reviewedBy").value("Bob"))
                .andExpect(jsonPath("$.rejectReason").value("匯率過高，請重新確認"));

        // The target entity was never touched by reject.
        assertThat(testAuditHandler.get(entityId)).containsEntry("name", "Original");
    }

    @Test
    void reject_returns400WhenRejectReasonMissing() throws Exception {
        Long entityId = testAuditHandler.seed(Map.of("name", "Original"));
        Long id = submitUpdate(entityId, Map.of("name", "Updated"));

        mockMvc.perform(post("/api/audit-requests/" + id + "/reject")
                        .contentType("application/json")
                        .content("{\"reviewedBy\":\"Bob\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.rejectReason").exists());
    }

    @Test
    void reject_returns400WhenRejectReasonBlank() throws Exception {
        Long entityId = testAuditHandler.seed(Map.of("name", "Original"));
        Long id = submitUpdate(entityId, Map.of("name", "Updated"));

        mockMvc.perform(post("/api/audit-requests/" + id + "/reject")
                        .contentType("application/json")
                        .content("{\"rejectReason\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reject_returns404WhenMissing() throws Exception {
        mockMvc.perform(post("/api/audit-requests/999999/reject")
                        .contentType("application/json")
                        .content("{\"rejectReason\":\"nope\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void reject_returns409WhenAlreadyRejected() throws Exception {
        Long entityId = testAuditHandler.seed(Map.of("name", "Original"));
        Long id = submitUpdate(entityId, Map.of("name", "Updated"));

        mockMvc.perform(post("/api/audit-requests/" + id + "/reject")
                        .contentType("application/json")
                        .content("{\"rejectReason\":\"first\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/audit-requests/" + id + "/reject")
                        .contentType("application/json")
                        .content("{\"rejectReason\":\"second\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Audit request has already been reviewed"))
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    // ---------- dedup (generic, no handler-specific code) ----------

    @Test
    void submit_secondPendingRequestForSameEntity_throwsDuplicate() {
        Long entityId = testAuditHandler.seed(Map.of("name", "Original"));
        submitUpdate(entityId, Map.of("name", "First proposal"));

        assertThatThrownBy(() -> submitUpdate(entityId, Map.of("name", "Second proposal")))
                .isInstanceOf(DuplicatePendingAuditRequestException.class);
    }

    @Test
    void submit_afterFirstRequestReviewed_allowsNewPendingRequest() throws Exception {
        Long entityId = testAuditHandler.seed(Map.of("name", "Original"));
        Long firstId = submitUpdate(entityId, Map.of("name", "First proposal"));

        mockMvc.perform(post("/api/audit-requests/" + firstId + "/reject")
                        .contentType("application/json")
                        .content("{\"rejectReason\":\"no\"}"))
                .andExpect(status().isOk());

        Long secondId = submitUpdate(entityId, Map.of("name", "Second proposal"));

        assertThat(secondId).isNotNull();
    }

    @Test
    void approve_withUnrecognizedEntityType_failsWithServerError() {
        // An entityType with no registered AuditHandler should never happen for rows this
        // service itself created (a handler was removed/renamed without a data migration) —
        // per the spec, this is a deliberately unmapped 500, not a normal error path.
        jdbcTemplate.update("INSERT INTO audit_request (entity_type, action_type, entity_id, after_snapshot, "
                        + "summary, status, requested_by) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "NO_HANDLER_REGISTERED", "UPDATE", 1L, "{}", "orphan", "PENDING", "Alice");
        Long id = jdbcTemplate.queryForObject("SELECT id FROM audit_request WHERE entity_type = 'NO_HANDLER_REGISTERED'", Long.class);

        assertThatThrownBy(() -> mockMvc.perform(
                        post("/api/audit-requests/" + id + "/approve").contentType("application/json").content("{}")))
                .hasRootCauseInstanceOf(IllegalStateException.class);
    }
}
