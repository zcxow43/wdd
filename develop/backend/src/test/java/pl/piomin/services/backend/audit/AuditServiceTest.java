package pl.piomin.services.backend.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Unit tests for the generic {@link AuditService}, exercised against a
 * Mockito-mocked {@link AuditHandler} (never a real, entity-specific
 * implementation such as {@code CurrencyPairAuditHandler}, which doesn't
 * exist yet). Proves the submit/approve/reject workflow and all generic
 * validation/dedup branches without any knowledge of a specific entity type.
 */
@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    private static final String ENTITY_TYPE = "TEST_ENTITY";

    @Mock
    private AuditRequestMapper auditRequestMapper;

    @Mock
    private AuditHandler handler;

    private AuditService auditService;

    @BeforeEach
    void setUp() {
        when(handler.entityType()).thenReturn(ENTITY_TYPE);
        auditService = new AuditService(auditRequestMapper, List.of(handler), new ObjectMapper());
    }

    private AuditRequest pendingRequest(Long id, String actionType, Long entityId, String afterSnapshot,
                                         String beforeSnapshot) {
        AuditRequest request = new AuditRequest();
        request.setId(id);
        request.setEntityType(ENTITY_TYPE);
        request.setActionType(actionType);
        request.setEntityId(entityId);
        request.setAfterSnapshot(afterSnapshot);
        request.setBeforeSnapshot(beforeSnapshot);
        request.setStatus("PENDING");
        request.setRequestedBy("Alice");
        request.setRequestedAt(LocalDateTime.now());
        return request;
    }

    // --- list / getById -----------------------------------------------------

    @Test
    void list_delegatesToMapper() {
        when(auditRequestMapper.findAll("TEST_ENTITY", "PENDING", null))
                .thenReturn(List.of(pendingRequest(1L, "CREATE", null, "{\"name\":\"Foo\"}", null)));

        List<AuditRequest> result = auditService.list("TEST_ENTITY", "PENDING", null);

        assertThat(result).hasSize(1);
        verify(auditRequestMapper).findAll("TEST_ENTITY", "PENDING", null);
    }

    @Test
    void getById_throwsNotFound_whenMissing() {
        when(auditRequestMapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> auditService.getById(999L))
                .isInstanceOf(AuditRequestNotFoundException.class);
    }

    // --- submit ---------------------------------------------------------------

    @Test
    void submit_create_insertsPendingRequestWithNullBefore() {
        Map<String, Object> after = Map.of("name", "Foo");
        when(handler.summarize(after)).thenReturn("TEST · Foo");
        doThrowNothingOnValidate();
        withInsertAssigningId(42L);
        when(auditRequestMapper.findById(42L))
                .thenReturn(pendingRequest(42L, "CREATE", null, "{\"name\":\"Foo\"}", null));

        AuditRequest result = auditService.submit(ENTITY_TYPE, AuditActionType.CREATE, null, after, "Alice");

        ArgumentCaptor<AuditRequest> captor = ArgumentCaptor.forClass(AuditRequest.class);
        verify(auditRequestMapper).insert(captor.capture());
        AuditRequest inserted = captor.getValue();
        assertThat(inserted.getStatus()).isEqualTo("PENDING");
        assertThat(inserted.getBeforeSnapshot()).isNull();
        assertThat(inserted.getAfterSnapshot()).contains("Foo");
        assertThat(inserted.getSummary()).isEqualTo("TEST · Foo");
        assertThat(inserted.getRequestedBy()).isEqualTo("Alice");
        assertThat(result.getId()).isEqualTo(42L);
        verify(handler, never()).snapshotOf(any());
    }

    @Test
    void submit_update_capturesBeforeSnapshot_andChecksNoDuplicatePending() {
        Map<String, Object> before = Map.of("name", "Old");
        Map<String, Object> after = Map.of("name", "New");
        when(handler.snapshotOf(5L)).thenReturn(before);
        when(auditRequestMapper.findPendingByEntity(ENTITY_TYPE, 5L)).thenReturn(null);
        when(handler.summarize(after)).thenReturn("TEST · New");
        withInsertAssigningId(7L);
        when(auditRequestMapper.findById(7L))
                .thenReturn(pendingRequest(7L, "UPDATE", 5L, "{\"name\":\"New\"}", "{\"name\":\"Old\"}"));

        auditService.submit(ENTITY_TYPE, AuditActionType.UPDATE, 5L, after, "Alice");

        verify(handler).snapshotOf(5L);
        verify(handler).validate(AuditActionType.UPDATE, 5L, after);
        ArgumentCaptor<AuditRequest> captor = ArgumentCaptor.forClass(AuditRequest.class);
        verify(auditRequestMapper).insert(captor.capture());
        assertThat(captor.getValue().getBeforeSnapshot()).contains("Old");
    }

    @Test
    void submit_throwsDuplicate_whenPendingRequestAlreadyExists() {
        Map<String, Object> before = Map.of("name", "Old");
        when(handler.snapshotOf(5L)).thenReturn(before);
        when(auditRequestMapper.findPendingByEntity(ENTITY_TYPE, 5L))
                .thenReturn(pendingRequest(1L, "UPDATE", 5L, "{}", "{}"));

        assertThatThrownBy(() -> auditService.submit(ENTITY_TYPE, AuditActionType.UPDATE, 5L,
                Map.of("name", "New"), "Alice"))
                .isInstanceOf(DuplicatePendingAuditRequestException.class);

        verify(handler, never()).validate(any(), any(), any());
        verify(auditRequestMapper, never()).insert(any());
    }

    @Test
    void submit_delete_doesNotCallValidate() {
        Map<String, Object> before = Map.of("name", "Old");
        when(handler.snapshotOf(9L)).thenReturn(before);
        when(auditRequestMapper.findPendingByEntity(ENTITY_TYPE, 9L)).thenReturn(null);
        when(handler.summarize(before)).thenReturn("TEST · Old");
        withInsertAssigningId(3L);
        when(auditRequestMapper.findById(3L))
                .thenReturn(pendingRequest(3L, "DELETE", 9L, null, "{\"name\":\"Old\"}"));

        auditService.submit(ENTITY_TYPE, AuditActionType.DELETE, 9L, null, "Alice");

        verify(handler, never()).validate(any(), any(), any());
        ArgumentCaptor<AuditRequest> captor = ArgumentCaptor.forClass(AuditRequest.class);
        verify(auditRequestMapper).insert(captor.capture());
        assertThat(captor.getValue().getAfterSnapshot()).isNull();
    }

    @Test
    void submit_throwsIllegalState_whenNoHandlerRegisteredForEntityType() {
        assertThatThrownBy(() -> auditService.submit("UNKNOWN_ENTITY", AuditActionType.CREATE, null,
                Map.of("name", "Foo"), "Alice"))
                .isInstanceOf(IllegalStateException.class);
    }

    // --- approve ---------------------------------------------------------------

    @Test
    void approve_create_appliesChange_setsEntityIdFromApplyResult() {
        AuditRequest pending = pendingRequest(10L, "CREATE", null, "{\"name\":\"Foo\"}", null);
        AuditRequest approved = pendingRequest(10L, "CREATE", 99L, "{\"name\":\"Foo\"}", null);
        approved.setStatus("APPROVED");
        when(auditRequestMapper.findById(10L)).thenReturn(pending, approved);
        when(handler.apply(eq(AuditActionType.CREATE), isNull(), eq(Map.of("name", "Foo")))).thenReturn(99L);

        AuditRequest result = auditService.approve(10L, "Bob");

        verify(handler).validate(AuditActionType.CREATE, null, Map.of("name", "Foo"));
        verify(handler).apply(AuditActionType.CREATE, null, Map.of("name", "Foo"));
        ArgumentCaptor<AuditRequest> captor = ArgumentCaptor.forClass(AuditRequest.class);
        verify(auditRequestMapper).update(captor.capture());
        AuditRequest updated = captor.getValue();
        assertThat(updated.getStatus()).isEqualTo("APPROVED");
        assertThat(updated.getEntityId()).isEqualTo(99L);
        assertThat(updated.getReviewedBy()).isEqualTo("Bob");
        assertThat(updated.getReviewedAt()).isNotNull();
        assertThat(result.getStatus()).isEqualTo("APPROVED");
    }

    @Test
    void approve_update_callsValidateThenApply_keepsEntityId() {
        AuditRequest pending = pendingRequest(11L, "UPDATE", 5L, "{\"name\":\"New\"}", "{\"name\":\"Old\"}");
        AuditRequest approved = pendingRequest(11L, "UPDATE", 5L, "{\"name\":\"New\"}", "{\"name\":\"Old\"}");
        approved.setStatus("APPROVED");
        when(auditRequestMapper.findById(11L)).thenReturn(pending, approved);
        when(handler.apply(AuditActionType.UPDATE, 5L, Map.of("name", "New"))).thenReturn(5L);

        auditService.approve(11L, "Bob");

        verify(handler).validate(AuditActionType.UPDATE, 5L, Map.of("name", "New"));
        ArgumentCaptor<AuditRequest> captor = ArgumentCaptor.forClass(AuditRequest.class);
        verify(auditRequestMapper).update(captor.capture());
        assertThat(captor.getValue().getEntityId()).isEqualTo(5L);
    }

    @Test
    void approve_delete_skipsValidate_callsApply() {
        AuditRequest pending = pendingRequest(12L, "DELETE", 9L, null, "{\"name\":\"Old\"}");
        AuditRequest approved = pendingRequest(12L, "DELETE", 9L, null, "{\"name\":\"Old\"}");
        approved.setStatus("APPROVED");
        when(auditRequestMapper.findById(12L)).thenReturn(pending, approved);
        when(handler.apply(AuditActionType.DELETE, 9L, null)).thenReturn(9L);

        auditService.approve(12L, "Bob");

        verify(handler, never()).validate(any(), any(), any());
        verify(handler).apply(AuditActionType.DELETE, 9L, null);
    }

    @Test
    void approve_throwsAlreadyReviewed_whenAlreadyApproved() {
        AuditRequest reviewed = pendingRequest(13L, "UPDATE", 5L, "{}", "{}");
        reviewed.setStatus("APPROVED");
        when(auditRequestMapper.findById(13L)).thenReturn(reviewed);

        assertThatThrownBy(() -> auditService.approve(13L, "Bob"))
                .isInstanceOf(AuditRequestAlreadyReviewedException.class);

        verify(handler, never()).apply(any(), any(), any());
        verify(auditRequestMapper, never()).update(any());
    }

    @Test
    void approve_throwsAlreadyReviewed_whenAlreadyRejected() {
        AuditRequest reviewed = pendingRequest(14L, "UPDATE", 5L, "{}", "{}");
        reviewed.setStatus("REJECTED");
        when(auditRequestMapper.findById(14L)).thenReturn(reviewed);

        assertThatThrownBy(() -> auditService.approve(14L, "Bob"))
                .isInstanceOf(AuditRequestAlreadyReviewedException.class);

        verify(handler, never()).apply(any(), any(), any());
        verify(auditRequestMapper, never()).update(any());
    }

    @Test
    void approve_leavesRequestPending_whenRevalidationFails() {
        AuditRequest pending = pendingRequest(15L, "UPDATE", 5L, "{\"name\":\"New\"}", "{\"name\":\"Old\"}");
        when(auditRequestMapper.findById(15L)).thenReturn(pending);
        doThrow(new IllegalArgumentException("state has drifted"))
                .when(handler).validate(AuditActionType.UPDATE, 5L, Map.of("name", "New"));

        assertThatThrownBy(() -> auditService.approve(15L, "Bob"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("state has drifted");

        verify(handler, never()).apply(any(), any(), any());
        verify(auditRequestMapper, never()).update(any());
        assertThat(pending.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void approve_throwsIllegalState_whenNoHandlerRegisteredForRequestEntityType() {
        AuditRequest pending = pendingRequest(16L, "UPDATE", 5L, "{}", "{}");
        pending.setEntityType("OTHER_ENTITY");
        when(auditRequestMapper.findById(16L)).thenReturn(pending);

        assertThatThrownBy(() -> auditService.approve(16L, "Bob"))
                .isInstanceOf(IllegalStateException.class);
    }

    // --- reject ---------------------------------------------------------------

    @Test
    void reject_marksRequestRejected_withoutTouchingHandler() {
        AuditRequest pending = pendingRequest(20L, "UPDATE", 5L, "{}", "{}");
        when(auditRequestMapper.findById(20L)).thenReturn(pending);

        auditService.reject(20L, "Bob", "匯率過高，請重新確認");

        ArgumentCaptor<AuditRequest> captor = ArgumentCaptor.forClass(AuditRequest.class);
        verify(auditRequestMapper).update(captor.capture());
        AuditRequest updated = captor.getValue();
        assertThat(updated.getStatus()).isEqualTo("REJECTED");
        assertThat(updated.getReviewedBy()).isEqualTo("Bob");
        assertThat(updated.getReviewedAt()).isNotNull();
        assertThat(updated.getRejectReason()).isEqualTo("匯率過高，請重新確認");
        verify(handler, never()).apply(any(), any(), any());
        verify(handler, never()).validate(any(), any(), any());
    }

    @Test
    void reject_throwsAlreadyReviewed_whenNotPending() {
        AuditRequest reviewed = pendingRequest(21L, "UPDATE", 5L, "{}", "{}");
        reviewed.setStatus("APPROVED");
        when(auditRequestMapper.findById(21L)).thenReturn(reviewed);

        assertThatThrownBy(() -> auditService.reject(21L, "Bob", "no good"))
                .isInstanceOf(AuditRequestAlreadyReviewedException.class);

        verify(auditRequestMapper, never()).update(any());
    }

    @Test
    void reject_throwsNotFound_whenMissing() {
        when(auditRequestMapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> auditService.reject(999L, "Bob", "no good"))
                .isInstanceOf(AuditRequestNotFoundException.class);
    }

    // --- helpers ---------------------------------------------------------------

    private void doThrowNothingOnValidate() {
        // default Mockito behavior for a void method is a no-op; kept as a named
        // no-op helper purely to make intent explicit at call sites above.
    }

    private void withInsertAssigningId(Long id) {
        org.mockito.Mockito.doAnswer(invocation -> {
            AuditRequest toInsert = invocation.getArgument(0);
            toInsert.setId(id);
            return 1;
        }).when(auditRequestMapper).insert(any(AuditRequest.class));
    }
}
