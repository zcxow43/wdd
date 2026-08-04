package com.wdd.backend.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Unit tests for {@link AuditService} against a Mockito-mocked {@link AuditHandler} — proving
 * the generic service works without any real consumer (e.g. {@code CurrencyPairAuditHandler}).
 */
@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    private static final String ENTITY_TYPE = "TEST_ENTITY";

    @Mock
    private AuditRequestMapper auditRequestMapper;

    @Mock
    private AuditHandler auditHandler;

    private AuditService auditService;

    @BeforeEach
    void setUp() {
        when(auditHandler.entityType()).thenReturn(ENTITY_TYPE);
        auditService = new AuditService(auditRequestMapper, List.of(auditHandler));
    }

    private AuditRequest sampleRequest(Long id, String status, String actionType, Long entityId) {
        AuditRequest request = new AuditRequest();
        request.setId(id);
        request.setEntityType(ENTITY_TYPE);
        request.setActionType(actionType);
        request.setEntityId(entityId);
        request.setBeforeSnapshot(actionType.equals("CREATE") ? null : "{\"name\":\"before\"}");
        request.setAfterSnapshot(actionType.equals("DELETE") ? null : "{\"name\":\"after\"}");
        request.setSummary("TEST_ENTITY · after");
        request.setStatus(status);
        request.setRequestedBy("Alice");
        request.setRequestedAt(LocalDateTime.now());
        request.setCreatedAt(LocalDateTime.now());
        request.setUpdatedAt(LocalDateTime.now());
        return request;
    }

    // ---------- list / getById ----------

    @Test
    void list_returnsAllRequestsMappedToResponses() {
        AuditRequest request = sampleRequest(1L, "PENDING", "UPDATE", 5L);
        when(auditRequestMapper.findAll(null, null, null)).thenReturn(List.of(request));

        List<AuditRequestResponse> result = auditService.list(null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEntityType()).isEqualTo(ENTITY_TYPE);
        assertThat(result.get(0).getAfter()).containsEntry("name", "after");
    }

    @Test
    void list_passesFiltersThrough() {
        when(auditRequestMapper.findAll(ENTITY_TYPE, "PENDING", "CREATE")).thenReturn(List.of());

        auditService.list(ENTITY_TYPE, "PENDING", "CREATE");

        verify(auditRequestMapper).findAll(ENTITY_TYPE, "PENDING", "CREATE");
    }

    @Test
    void getById_returnsResponseWhenFound() {
        when(auditRequestMapper.findById(1L)).thenReturn(Optional.of(sampleRequest(1L, "PENDING", "UPDATE", 5L)));

        AuditRequestResponse response = auditService.getById(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getBefore()).containsEntry("name", "before");
    }

    @Test
    void getById_throwsNotFoundWhenMissing() {
        when(auditRequestMapper.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auditService.getById(999L))
                .isInstanceOf(AuditRequestNotFoundException.class);
    }

    // ---------- submit ----------

    @Test
    void submit_create_validatesAndInsertsWithNullBefore() {
        Map<String, Object> after = Map.of("name", "new-thing");
        when(auditHandler.summarize(after)).thenReturn("TEST_ENTITY · new-thing");
        doAnswer(invocation -> {
            AuditRequest r = invocation.getArgument(0);
            r.setId(10L);
            return 1;
        }).when(auditRequestMapper).insert(any(AuditRequest.class));
        when(auditRequestMapper.findById(10L)).thenReturn(Optional.of(sampleRequest(10L, "PENDING", "CREATE", null)));

        AuditRequestResponse response = auditService.submit(ENTITY_TYPE, AuditActionType.CREATE, null, after, "Alice");

        assertThat(response.getId()).isEqualTo(10L);
        verify(auditHandler).validate(AuditActionType.CREATE, null, after);
        verify(auditHandler, never()).snapshotOf(any());
        verify(auditRequestMapper, never()).findPendingByEntity(any(), any());
    }

    @Test
    void submit_update_capturesBeforeSnapshotAndValidatesAfter() {
        Map<String, Object> before = Map.of("name", "old");
        Map<String, Object> after = Map.of("name", "new");
        when(auditHandler.snapshotOf(5L)).thenReturn(before);
        when(auditRequestMapper.findPendingByEntity(ENTITY_TYPE, 5L)).thenReturn(Optional.empty());
        when(auditHandler.summarize(before)).thenReturn("TEST_ENTITY · old");
        doAnswer(invocation -> {
            AuditRequest r = invocation.getArgument(0);
            r.setId(11L);
            return 1;
        }).when(auditRequestMapper).insert(any(AuditRequest.class));
        when(auditRequestMapper.findById(11L)).thenReturn(Optional.of(sampleRequest(11L, "PENDING", "UPDATE", 5L)));

        auditService.submit(ENTITY_TYPE, AuditActionType.UPDATE, 5L, after, "Alice");

        verify(auditHandler).snapshotOf(5L);
        verify(auditHandler).validate(AuditActionType.UPDATE, 5L, after);
    }

    @Test
    void submit_delete_capturesBeforeSnapshotButDoesNotValidate() {
        Map<String, Object> before = Map.of("name", "old");
        when(auditHandler.snapshotOf(5L)).thenReturn(before);
        when(auditRequestMapper.findPendingByEntity(ENTITY_TYPE, 5L)).thenReturn(Optional.empty());
        when(auditHandler.summarize(before)).thenReturn("TEST_ENTITY · old");
        doAnswer(invocation -> {
            AuditRequest r = invocation.getArgument(0);
            r.setId(12L);
            return 1;
        }).when(auditRequestMapper).insert(any(AuditRequest.class));
        when(auditRequestMapper.findById(12L)).thenReturn(Optional.of(sampleRequest(12L, "PENDING", "DELETE", 5L)));

        auditService.submit(ENTITY_TYPE, AuditActionType.DELETE, 5L, null, "Alice");

        verify(auditHandler).snapshotOf(5L);
        verify(auditHandler, never()).validate(any(), any(), any());
    }

    @Test
    void submit_update_throwsDuplicateWhenPendingRequestExists() {
        when(auditHandler.snapshotOf(5L)).thenReturn(Map.of("name", "old"));
        when(auditRequestMapper.findPendingByEntity(ENTITY_TYPE, 5L))
                .thenReturn(Optional.of(sampleRequest(99L, "PENDING", "UPDATE", 5L)));

        assertThatThrownBy(() -> auditService.submit(ENTITY_TYPE, AuditActionType.UPDATE, 5L, Map.of("name", "new"), "Alice"))
                .isInstanceOf(DuplicatePendingAuditRequestException.class);

        verify(auditRequestMapper, never()).insert(any());
    }

    @Test
    void submit_create_propagatesHandlerValidationFailure() {
        Map<String, Object> after = Map.of("name", "dup");
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "duplicate"))
                .when(auditHandler).validate(AuditActionType.CREATE, null, after);

        assertThatThrownBy(() -> auditService.submit(ENTITY_TYPE, AuditActionType.CREATE, null, after, "Alice"))
                .isInstanceOf(ResponseStatusException.class);

        verify(auditRequestMapper, never()).insert(any());
    }

    // ---------- approve ----------

    @Test
    void approve_onPendingRequest_validatesAppliesAndMarksApproved() {
        AuditRequest pending = sampleRequest(1L, "PENDING", "UPDATE", 5L);
        when(auditRequestMapper.findById(1L)).thenReturn(Optional.of(pending), Optional.of(pending));
        when(auditHandler.apply(eq(AuditActionType.UPDATE), eq(5L), any())).thenReturn(5L);

        AuditRequestResponse response = auditService.approve(1L, "Bob");

        verify(auditHandler).validate(eq(AuditActionType.UPDATE), eq(5L), any());
        verify(auditHandler).apply(eq(AuditActionType.UPDATE), eq(5L), any());
        verify(auditRequestMapper).update(any(AuditRequest.class));
        assertThat(response.getStatus()).isEqualTo("APPROVED");
        assertThat(response.getReviewedBy()).isEqualTo("Bob");
        assertThat(response.getReviewedAt()).isNotNull();
    }

    @Test
    void approve_create_setsEntityIdFromApplyResult() {
        AuditRequest pending = sampleRequest(2L, "PENDING", "CREATE", null);
        when(auditRequestMapper.findById(2L)).thenReturn(Optional.of(pending), Optional.of(pending));
        when(auditHandler.apply(eq(AuditActionType.CREATE), isNull(), any())).thenReturn(42L);

        auditService.approve(2L, "Bob");

        verify(auditRequestMapper).update(argThatEntityIdIs(42L));
    }

    private AuditRequest argThatEntityIdIs(Long expected) {
        return org.mockito.ArgumentMatchers.argThat(r -> expected.equals(r.getEntityId()));
    }

    @Test
    void approve_delete_doesNotReValidate() {
        AuditRequest pending = sampleRequest(3L, "PENDING", "DELETE", 5L);
        when(auditRequestMapper.findById(3L)).thenReturn(Optional.of(pending), Optional.of(pending));
        when(auditHandler.apply(eq(AuditActionType.DELETE), eq(5L), isNull())).thenReturn(5L);

        auditService.approve(3L, "Bob");

        verify(auditHandler, never()).validate(any(), any(), any());
        verify(auditHandler).apply(eq(AuditActionType.DELETE), eq(5L), isNull());
    }

    @Test
    void approve_throwsNotFoundWhenMissing() {
        when(auditRequestMapper.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auditService.approve(999L, "Bob"))
                .isInstanceOf(AuditRequestNotFoundException.class);
    }

    @Test
    void approve_throwsAlreadyReviewedWhenNotPending() {
        when(auditRequestMapper.findById(1L)).thenReturn(Optional.of(sampleRequest(1L, "APPROVED", "UPDATE", 5L)));

        assertThatThrownBy(() -> auditService.approve(1L, "Bob"))
                .isInstanceOf(AuditRequestAlreadyReviewedException.class);

        verify(auditHandler, never()).apply(any(), any(), any());
    }

    @Test
    void approve_leavesRequestPending_whenRevalidationFails() {
        AuditRequest pending = sampleRequest(1L, "PENDING", "UPDATE", 5L);
        when(auditRequestMapper.findById(1L)).thenReturn(Optional.of(pending));
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "state drifted"))
                .when(auditHandler).validate(eq(AuditActionType.UPDATE), eq(5L), any());

        assertThatThrownBy(() -> auditService.approve(1L, "Bob"))
                .isInstanceOf(ResponseStatusException.class);

        verify(auditHandler, never()).apply(any(), any(), any());
        verify(auditRequestMapper, never()).update(any());
    }

    // ---------- reject ----------

    @Test
    void reject_onPendingRequest_marksRejectedWithoutTouchingHandler() {
        AuditRequest pending = sampleRequest(1L, "PENDING", "UPDATE", 5L);
        when(auditRequestMapper.findById(1L)).thenReturn(Optional.of(pending), Optional.of(pending));

        AuditRequestResponse response = auditService.reject(1L, "Bob", "not needed");

        assertThat(response.getStatus()).isEqualTo("REJECTED");
        assertThat(response.getRejectReason()).isEqualTo("not needed");
        verify(auditHandler, never()).apply(any(), any(), any());
        verify(auditHandler, never()).validate(any(), any(), any());
    }

    @Test
    void reject_throwsNotFoundWhenMissing() {
        when(auditRequestMapper.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auditService.reject(999L, "Bob", "reason"))
                .isInstanceOf(AuditRequestNotFoundException.class);
    }

    @Test
    void reject_throwsAlreadyReviewedWhenNotPending() {
        when(auditRequestMapper.findById(1L)).thenReturn(Optional.of(sampleRequest(1L, "REJECTED", "UPDATE", 5L)));

        assertThatThrownBy(() -> auditService.reject(1L, "Bob", "reason"))
                .isInstanceOf(AuditRequestAlreadyReviewedException.class);

        verify(auditRequestMapper, never()).update(any());
    }
}
