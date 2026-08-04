package com.wdd.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.wdd.backend.audit.AuditActionType;
import com.wdd.backend.dto.SpreadDefaultResponse;
import com.wdd.backend.exception.InvalidSpreadException;
import com.wdd.backend.exception.SpreadDefaultNotFoundException;

/**
 * Unit tests for {@link SpreadDefaultAuditHandler} — the SPREAD_DEFAULT plug-in for the generic
 * audit workflow (specs/backend/audit.md, specs/backend/spread.md). Handles UPDATE only.
 */
@ExtendWith(MockitoExtension.class)
class SpreadDefaultAuditHandlerTest {

    @Mock
    private SpreadDefaultService spreadDefaultService;

    @Mock
    private SpreadGroupValidator validator;

    private SpreadDefaultAuditHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SpreadDefaultAuditHandler(spreadDefaultService, validator);
    }

    private SpreadDefaultResponse sampleResponse() {
        SpreadDefaultResponse response = new SpreadDefaultResponse();
        response.setId(1L);
        response.setBrandId(3L);
        response.setBrandCode("AU");
        response.setDepositSpread(BigDecimal.ZERO);
        response.setWithdrawSpread(BigDecimal.ZERO);
        return response;
    }

    private Map<String, Object> proposedAfter() {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("depositSpread", new BigDecimal("0.1"));
        after.put("withdrawSpread", new BigDecimal("0.2"));
        return after;
    }

    // ---------- entityType ----------

    @Test
    void entityType_returnsSpreadDefault() {
        assertThat(handler.entityType()).isEqualTo("SPREAD_DEFAULT");
    }

    // ---------- snapshotOf ----------

    @Test
    void snapshotOf_returnsShapeMatchingSpec() {
        when(spreadDefaultService.getById(1L)).thenReturn(sampleResponse());

        Map<String, Object> snapshot = handler.snapshotOf(1L);

        assertThat(snapshot).containsEntry("brandId", 3L)
                .containsEntry("brandCode", "AU");
        assertThat((BigDecimal) snapshot.get("depositSpread")).isEqualByComparingTo("0");
        assertThat((BigDecimal) snapshot.get("withdrawSpread")).isEqualByComparingTo("0");
    }

    @Test
    void snapshotOf_propagatesNotFound_whenMissing() {
        when(spreadDefaultService.getById(999L)).thenThrow(new SpreadDefaultNotFoundException(999L));

        assertThatThrownBy(() -> handler.snapshotOf(999L))
                .isInstanceOf(SpreadDefaultNotFoundException.class);
    }

    // ---------- validate ----------

    @Test
    void validate_success_enrichesSnapshotWithBrandIdAndCode() {
        when(spreadDefaultService.getById(1L)).thenReturn(sampleResponse());

        Map<String, Object> after = proposedAfter();
        handler.validate(AuditActionType.UPDATE, 1L, after);

        assertThat(after).containsEntry("brandId", 3L)
                .containsEntry("brandCode", "AU");
        assertThat((BigDecimal) after.get("depositSpread")).isEqualByComparingTo("0.1");
        assertThat((BigDecimal) after.get("withdrawSpread")).isEqualByComparingTo("0.2");
        verify(validator).requireSpreadNonNegative(eq(new BigDecimal("0.1")), eq(new BigDecimal("0.2")));
    }

    @Test
    void validate_throwsInvalidSpread_whenDepositNegative() {
        doThrow(new InvalidSpreadException("depositSpread is required and must be >= 0"))
                .when(validator).requireSpreadNonNegative(any(), any());

        Map<String, Object> after = proposedAfter();
        after.put("depositSpread", new BigDecimal("-1"));

        assertThatThrownBy(() -> handler.validate(AuditActionType.UPDATE, 1L, after))
                .isInstanceOf(InvalidSpreadException.class);

        verify(spreadDefaultService, never()).getById(any());
    }

    @Test
    void validate_throwsInvalidSpread_whenWithdrawNegative() {
        doThrow(new InvalidSpreadException("withdrawSpread is required and must be >= 0"))
                .when(validator).requireSpreadNonNegative(any(), any());

        Map<String, Object> after = proposedAfter();
        after.put("withdrawSpread", new BigDecimal("-1"));

        assertThatThrownBy(() -> handler.validate(AuditActionType.UPDATE, 1L, after))
                .isInstanceOf(InvalidSpreadException.class);
    }

    @Test
    void validate_propagatesNotFound_whenRowNoLongerExists() {
        when(spreadDefaultService.getById(1L)).thenThrow(new SpreadDefaultNotFoundException(1L));

        assertThatThrownBy(() -> handler.validate(AuditActionType.UPDATE, 1L, proposedAfter()))
                .isInstanceOf(SpreadDefaultNotFoundException.class);
    }

    // ---------- apply ----------

    @Test
    void apply_create_throwsUnsupportedOperation() {
        assertThatThrownBy(() -> handler.apply(AuditActionType.CREATE, null, proposedAfter()))
                .isInstanceOf(UnsupportedOperationException.class);

        verify(spreadDefaultService, never()).update(any(), any(), any());
    }

    @Test
    void apply_update_callsServiceUpdate_andReturnsEntityId() {
        Long resultId = handler.apply(AuditActionType.UPDATE, 1L, proposedAfter());

        assertThat(resultId).isEqualTo(1L);
        verify(spreadDefaultService).update(eq(1L), eq(new BigDecimal("0.1")), eq(new BigDecimal("0.2")));
    }

    @Test
    void apply_delete_throwsUnsupportedOperation() {
        assertThatThrownBy(() -> handler.apply(AuditActionType.DELETE, 1L, null))
                .isInstanceOf(UnsupportedOperationException.class);

        verify(spreadDefaultService, never()).update(any(), any(), any());
    }

    // ---------- summarize ----------

    @Test
    void summarize_formatsBrandCodeAndLabel() {
        when(spreadDefaultService.getById(1L)).thenReturn(sampleResponse());
        Map<String, Object> snapshot = handler.snapshotOf(1L);

        String summary = handler.summarize(snapshot);

        assertThat(summary).isEqualTo("AU · 預設點差");
    }

    @Test
    void summarize_returnsEntityType_whenSnapshotNull() {
        assertThat(handler.summarize(null)).isEqualTo("SPREAD_DEFAULT");
    }
}
