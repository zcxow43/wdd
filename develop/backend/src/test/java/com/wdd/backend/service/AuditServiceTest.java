package com.wdd.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import com.wdd.backend.dto.AuditActionRequest;
import com.wdd.backend.dto.AuditRequest;
import com.wdd.backend.dto.AuditRequestDetailResponse;
import com.wdd.backend.dto.AuditRequestSummaryResponse;
import com.wdd.backend.exception.AuditApplyFailedException;
import com.wdd.backend.exception.AuditHandlerException;
import com.wdd.backend.exception.AuditRequestConflictException;
import com.wdd.backend.exception.AuditRequestNotFoundException;
import com.wdd.backend.exception.InvalidRequestException;
import com.wdd.backend.mapper.AuditRequestMapper;

class AuditServiceTest {

    private AuditRequestMapper auditRequestMapper;
    private AuditHandlerRegistry handlerRegistry;
    private AuditApplyRunner applyRunner;
    private AuditService service;

    @BeforeEach
    void setUp() {
        auditRequestMapper = mock(AuditRequestMapper.class);
        handlerRegistry = mock(AuditHandlerRegistry.class);
        applyRunner = mock(AuditApplyRunner.class);
        service = new AuditService(auditRequestMapper, handlerRegistry, applyRunner);
    }

    private static AuditRequest sampleRequest(Long id, String status) {
        AuditRequest r = new AuditRequest();
        r.setId(id);
        r.setEntityType("TEST_STUB");
        r.setActionType("UPDATE");
        r.setEntityId(100L);
        r.setBrandId(1L);
        r.setSummary("test change");
        r.setBeforeData(Map.of("value", "old"));
        r.setAfterData(Map.of("value", "new"));
        r.setStatus(status);
        r.setRequestedBy("alice");
        r.setRequestedAt(LocalDateTime.now());
        return r;
    }

    // --- findAll / findById ---

    @Test
    void findAllDelegatesFiltersAndOmitsBeforeAfterData() {
        when(auditRequestMapper.findAll("PENDING", "TEST_STUB", 1L, 100L))
                .thenReturn(List.of(sampleRequest(1L, "PENDING")));

        List<AuditRequestSummaryResponse> result = service.findAll("PENDING", "TEST_STUB", 1L, 100L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        verify(auditRequestMapper).findAll("PENDING", "TEST_STUB", 1L, 100L);
    }

    @Test
    void findByIdReturnsDetailWithBeforeAndAfterData() {
        when(auditRequestMapper.findById(1L)).thenReturn(sampleRequest(1L, "PENDING"));

        AuditRequestDetailResponse response = service.findById(1L);

        assertThat(response.getBeforeData()).isEqualTo(Map.of("value", "old"));
        assertThat(response.getAfterData()).isEqualTo(Map.of("value", "new"));
    }

    @Test
    void findByIdThrowsNotFoundForUnknownId() {
        when(auditRequestMapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.findById(999L)).isInstanceOf(AuditRequestNotFoundException.class);
    }

    // --- submit ---

    @Test
    void submitRejectsWhenPendingAlreadyExistsForTarget() {
        when(auditRequestMapper.findPending("TEST_STUB", 100L)).thenReturn(sampleRequest(1L, "PENDING"));

        assertThatThrownBy(() -> service.submit("TEST_STUB", "UPDATE", 100L, 1L, "summary", null,
                Map.of("value", "x"), "bob"))
                .isInstanceOf(AuditRequestConflictException.class);

        verify(auditRequestMapper, never()).insert(any());
    }

    @Test
    void submitInsertsAndDefaultsActorToSystemWhenBlank() {
        when(auditRequestMapper.findPending("TEST_STUB", 100L)).thenReturn(null);
        when(auditRequestMapper.findById(any())).thenReturn(sampleRequest(1L, "PENDING"));

        service.submit("TEST_STUB", "UPDATE", 100L, 1L, "summary", null, Map.of("value", "x"), "  ");

        verify(auditRequestMapper).insert(argThatRequestedByIsSystem());
    }

    private static AuditRequest argThatRequestedByIsSystem() {
        return org.mockito.ArgumentMatchers.argThat(r -> "system".equals(r.getRequestedBy()));
    }

    @Test
    void submitConvertsDuplicateKeyExceptionToConflict() {
        when(auditRequestMapper.findPending("TEST_STUB", 100L)).thenReturn(null);
        doThrow(new DuplicateKeyException("uk_audit_request_pending")).when(auditRequestMapper).insert(any());

        assertThatThrownBy(() -> service.submit("TEST_STUB", "UPDATE", 100L, 1L, "summary", null,
                Map.of("value", "x"), "bob"))
                .isInstanceOf(AuditRequestConflictException.class);
    }

    @Test
    void submitRejectsBlankSummary() {
        assertThatThrownBy(() -> service.submit("TEST_STUB", "UPDATE", 100L, 1L, "  ", null,
                Map.of("value", "x"), "bob"))
                .isInstanceOf(InvalidRequestException.class);
    }

    // --- approve ---

    @Test
    void approveThrowsNotFoundForUnknownId() {
        when(auditRequestMapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.approve(999L, null, "bob"))
                .isInstanceOf(AuditRequestNotFoundException.class);
    }

    @Test
    void approveThrowsConflictWhenNotPending() {
        when(auditRequestMapper.findById(1L)).thenReturn(sampleRequest(1L, "APPROVED"));

        assertThatThrownBy(() -> service.approve(1L, null, "bob"))
                .isInstanceOf(AuditRequestConflictException.class);
        verify(handlerRegistry, never()).resolve(anyString());
    }

    @Test
    void approveRunsHandlerThenReturnsUpdatedRequest() {
        AuditRequest pending = sampleRequest(1L, "PENDING");
        AuditRequest approved = sampleRequest(1L, "APPROVED");
        when(auditRequestMapper.findById(1L)).thenReturn(pending).thenReturn(approved);
        AuditHandler handler = mock(AuditHandler.class);
        when(handlerRegistry.resolve("TEST_STUB")).thenReturn(handler);

        AuditRequestDetailResponse response = service.approve(1L, new AuditActionRequest("looks good"), "carol");

        assertThat(response.getStatus()).isEqualTo("APPROVED");
        verify(applyRunner).run(eq(pending), eq(handler), eq("carol"), eq("looks good"));
        verify(auditRequestMapper, never()).updateApplyError(any(), anyString());
    }

    @Test
    void approveDefaultsActorToSystemWhenHeaderAbsent() {
        AuditRequest pending = sampleRequest(1L, "PENDING");
        when(auditRequestMapper.findById(1L)).thenReturn(pending);
        AuditHandler handler = mock(AuditHandler.class);
        when(handlerRegistry.resolve("TEST_STUB")).thenReturn(handler);

        service.approve(1L, null, null);

        verify(applyRunner).run(eq(pending), eq(handler), eq("system"), isNull());
    }

    @Test
    void approveRecordsApplyErrorAndReturns422WhenHandlerRejects() {
        AuditRequest pending = sampleRequest(1L, "PENDING");
        when(auditRequestMapper.findById(1L)).thenReturn(pending);
        AuditHandler handler = mock(AuditHandler.class);
        when(handlerRegistry.resolve("TEST_STUB")).thenReturn(handler);
        doThrow(new AuditHandlerException("target drifted")).when(applyRunner)
                .run(any(), any(), anyString(), any());

        assertThatThrownBy(() -> service.approve(1L, null, "carol"))
                .isInstanceOf(AuditApplyFailedException.class)
                .satisfies(ex -> {
                    AuditApplyFailedException failed = (AuditApplyFailedException) ex;
                    assertThat(failed.getAuditRequestId()).isEqualTo(1L);
                    assertThat(failed.getMessage()).isEqualTo("target drifted");
                });

        verify(auditRequestMapper).updateApplyError(1L, "target drifted");
        // request status is never touched by the service on this path — it stays PENDING.
        verify(auditRequestMapper, never()).updateResolved(any(), anyString(), anyString(), any(), any(), any());
    }

    // --- reject ---

    @Test
    void rejectRequiresNonBlankComment() {
        when(auditRequestMapper.findById(1L)).thenReturn(sampleRequest(1L, "PENDING"));

        assertThatThrownBy(() -> service.reject(1L, new AuditActionRequest("  "), "bob"))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.reject(1L, null, "bob"))
                .isInstanceOf(InvalidRequestException.class);

        verify(auditRequestMapper, never()).updateResolved(any(), anyString(), anyString(), any(), any(), any());
    }

    @Test
    void rejectMarksRejectedWithComment() {
        when(auditRequestMapper.findById(1L))
                .thenReturn(sampleRequest(1L, "PENDING"))
                .thenReturn(sampleRequest(1L, "REJECTED"));

        AuditRequestDetailResponse response = service.reject(1L, new AuditActionRequest("bad idea"), "carol");

        assertThat(response.getStatus()).isEqualTo("REJECTED");
        verify(auditRequestMapper).updateResolved(eq(1L), eq("REJECTED"), eq("carol"), any(), eq("bad idea"),
                isNull());
    }

    @Test
    void rejectThrowsConflictWhenNotPending() {
        when(auditRequestMapper.findById(1L)).thenReturn(sampleRequest(1L, "CANCELLED"));

        assertThatThrownBy(() -> service.reject(1L, new AuditActionRequest("x"), "bob"))
                .isInstanceOf(AuditRequestConflictException.class);
    }

    // --- cancel ---

    @Test
    void cancelMarksCancelledWithOptionalComment() {
        when(auditRequestMapper.findById(1L))
                .thenReturn(sampleRequest(1L, "PENDING"))
                .thenReturn(sampleRequest(1L, "CANCELLED"));

        AuditRequestDetailResponse response = service.cancel(1L, null, "dave");

        assertThat(response.getStatus()).isEqualTo("CANCELLED");
        verify(auditRequestMapper).updateResolved(eq(1L), eq("CANCELLED"), eq("dave"), any(), isNull(), isNull());
    }

    @Test
    void cancelThrowsConflictWhenNotPending() {
        when(auditRequestMapper.findById(1L)).thenReturn(sampleRequest(1L, "REJECTED"));

        assertThatThrownBy(() -> service.cancel(1L, null, "bob"))
                .isInstanceOf(AuditRequestConflictException.class);
    }
}
