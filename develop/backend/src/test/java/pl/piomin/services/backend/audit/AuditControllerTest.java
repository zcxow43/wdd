package pl.piomin.services.backend.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Integration tests for the generic {@code /api/audit-requests} API, driven
 * entirely against {@link TestAuditHandler} - a test-only fake registered
 * purely for these tests - proving the module works end-to-end with zero real
 * domain consumer (e.g. {@code CurrencyPairAuditHandler}) wired in.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuditControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditService auditService;

    @Autowired
    private AuditRequestMapper auditRequestMapper;

    @Autowired
    private TestAuditHandler testAuditHandler;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        for (Long id : auditRequestMapper.findAllIds()) {
            auditRequestMapper.deleteById(id);
        }
        testAuditHandler.reset();
    }

    private Map<String, Object> nameSnapshot(String name) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", name);
        return map;
    }

    @Test
    void list_filtersByEntityTypeAndStatus() throws Exception {
        auditService.submit(TestAuditHandler.ENTITY_TYPE, AuditActionType.CREATE, null,
                nameSnapshot("Foo"), "Alice");
        Long existingId = testAuditHandler.seed(nameSnapshot("Bar"));
        AuditRequest toApprove = auditService.submit(TestAuditHandler.ENTITY_TYPE, AuditActionType.UPDATE,
                existingId, nameSnapshot("BarUpdated"), "Alice");
        auditService.approve(toApprove.getId(), "Bob");

        mockMvc.perform(get("/api/audit-requests")
                        .param("entityType", TestAuditHandler.ENTITY_TYPE)
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].actionType").value("CREATE"));

        mockMvc.perform(get("/api/audit-requests")
                        .param("entityType", TestAuditHandler.ENTITY_TYPE)
                        .param("status", "APPROVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].actionType").value("UPDATE"));
    }

    @Test
    void getById_returnsRequest_whenFound() throws Exception {
        AuditRequest request = auditService.submit(TestAuditHandler.ENTITY_TYPE, AuditActionType.CREATE, null,
                nameSnapshot("Foo"), "Alice");

        mockMvc.perform(get("/api/audit-requests/{id}", request.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entityType").value(TestAuditHandler.ENTITY_TYPE))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.before").doesNotExist())
                .andExpect(jsonPath("$.after.name").value("Foo"))
                .andExpect(jsonPath("$.summary").value("TEST · Foo"));
    }

    @Test
    void getById_returns404_whenNotFound() throws Exception {
        mockMvc.perform(get("/api/audit-requests/{id}", 999999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Audit request not found"))
                .andExpect(jsonPath("$.id").value(999999));
    }

    @Test
    void approve_create_setsApprovedStatusAndEntityIdFromApply() throws Exception {
        AuditRequest request = auditService.submit(TestAuditHandler.ENTITY_TYPE, AuditActionType.CREATE, null,
                nameSnapshot("Foo"), "Alice");

        String body = objectMapper.writeValueAsString(Map.of("reviewedBy", "Bob"));

        mockMvc.perform(post("/api/audit-requests/{id}/approve", request.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.reviewedBy").value("Bob"))
                .andExpect(jsonPath("$.entityId").exists());

        AuditRequest updated = auditRequestMapper.findById(request.getId());
        assertThat(updated.getStatus()).isEqualTo("APPROVED");
        assertThat(testAuditHandler.exists(updated.getEntityId())).isTrue();
    }

    @Test
    void approve_withoutBody_treatsReviewedByAsNull() throws Exception {
        AuditRequest request = auditService.submit(TestAuditHandler.ENTITY_TYPE, AuditActionType.CREATE, null,
                nameSnapshot("Foo"), "Alice");

        mockMvc.perform(post("/api/audit-requests/{id}/approve", request.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.reviewedBy").doesNotExist());
    }

    @Test
    void approve_returns400_andLeavesRequestPending_whenRevalidationFails() throws Exception {
        Long existingId = testAuditHandler.seed(nameSnapshot("Bar"));
        AuditRequest request = auditService.submit(TestAuditHandler.ENTITY_TYPE, AuditActionType.UPDATE,
                existingId, nameSnapshot("BarUpdated"), "Alice");

        testAuditHandler.setRejectNextValidation(true);

        mockMvc.perform(post("/api/audit-requests/{id}/approve", request.getId()))
                .andExpect(status().isBadRequest());

        AuditRequest stillPending = auditRequestMapper.findById(request.getId());
        assertThat(stillPending.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void approve_returns404_whenRequestNotFound() throws Exception {
        mockMvc.perform(post("/api/audit-requests/{id}/approve", 999999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Audit request not found"));
    }

    @Test
    void approve_returns409_whenAlreadyReviewed() throws Exception {
        AuditRequest request = auditService.submit(TestAuditHandler.ENTITY_TYPE, AuditActionType.CREATE, null,
                nameSnapshot("Foo"), "Alice");
        auditService.approve(request.getId(), "Bob");

        mockMvc.perform(post("/api/audit-requests/{id}/approve", request.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Audit request has already been reviewed"))
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void reject_marksRequestRejected() throws Exception {
        AuditRequest request = auditService.submit(TestAuditHandler.ENTITY_TYPE, AuditActionType.CREATE, null,
                nameSnapshot("Foo"), "Alice");

        String body = objectMapper.writeValueAsString(Map.of("reviewedBy", "Bob", "rejectReason", "匯率過高，請重新確認"));

        mockMvc.perform(post("/api/audit-requests/{id}/reject", request.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.reviewedBy").value("Bob"))
                .andExpect(jsonPath("$.rejectReason").value("匯率過高，請重新確認"));
    }

    @Test
    void reject_neverAppliesChange_toTargetEntity() throws Exception {
        Long existingId = testAuditHandler.seed(nameSnapshot("Bar"));
        AuditRequest request = auditService.submit(TestAuditHandler.ENTITY_TYPE, AuditActionType.UPDATE,
                existingId, nameSnapshot("BarUpdated"), "Alice");

        String body = objectMapper.writeValueAsString(Map.of("rejectReason", "no good"));

        mockMvc.perform(post("/api/audit-requests/{id}/reject", request.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        assertThat(testAuditHandler.snapshotOf(existingId)).containsEntry("name", "Bar");
    }

    @Test
    void reject_returns400_whenRejectReasonMissing() throws Exception {
        AuditRequest request = auditService.submit(TestAuditHandler.ENTITY_TYPE, AuditActionType.CREATE, null,
                nameSnapshot("Foo"), "Alice");

        String body = objectMapper.writeValueAsString(Map.of("reviewedBy", "Bob"));

        mockMvc.perform(post("/api/audit-requests/{id}/reject", request.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.rejectReason").exists());
    }

    @Test
    void reject_returns409_whenAlreadyReviewed() throws Exception {
        AuditRequest request = auditService.submit(TestAuditHandler.ENTITY_TYPE, AuditActionType.CREATE, null,
                nameSnapshot("Foo"), "Alice");
        auditService.reject(request.getId(), "Bob", "first rejection");

        String body = objectMapper.writeValueAsString(Map.of("rejectReason", "second rejection"));

        mockMvc.perform(post("/api/audit-requests/{id}/reject", request.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void reject_returns404_whenNotFound() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("rejectReason", "no good"));

        mockMvc.perform(post("/api/audit-requests/{id}/reject", 999999)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void submit_returns409_whenDuplicatePendingRequestExistsForSameEntity() {
        Long existingId = testAuditHandler.seed(nameSnapshot("Bar"));
        auditService.submit(TestAuditHandler.ENTITY_TYPE, AuditActionType.UPDATE, existingId,
                nameSnapshot("BarUpdated1"), "Alice");

        org.junit.jupiter.api.Assertions.assertThrows(DuplicatePendingAuditRequestException.class,
                () -> auditService.submit(TestAuditHandler.ENTITY_TYPE, AuditActionType.UPDATE, existingId,
                        nameSnapshot("BarUpdated2"), "Alice"));
    }
}
