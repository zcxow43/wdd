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
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.wdd.backend.audit.AuditActionType;
import com.wdd.backend.audit.AuditRequest;
import com.wdd.backend.audit.AuditRequestMapper;
import com.wdd.backend.dto.SpreadGroupMemberResponse;
import com.wdd.backend.dto.SpreadGroupResponse;
import com.wdd.backend.exception.BrandNotFoundException;
import com.wdd.backend.exception.CurrencyPairNotFoundException;
import com.wdd.backend.exception.DuplicatePendingSpreadGroupCreateException;
import com.wdd.backend.exception.DuplicateSpreadGroupMemberException;
import com.wdd.backend.exception.InvalidSpreadException;
import com.wdd.backend.exception.InvalidSpreadGroupMemberException;
import com.wdd.backend.exception.SpreadGroupNameExistsException;
import com.wdd.backend.exception.SpreadGroupNotFoundException;
import com.wdd.backend.mapper.CurrencyPairMapper;
import com.wdd.backend.mapper.SpreadGroupMemberMapper;
import com.wdd.backend.model.Brand;
import com.wdd.backend.model.CurrencyPair;
import com.wdd.backend.model.SpreadGroupMember;

/**
 * Unit tests for {@link SpreadGroupAuditHandler} — the SPREAD_GROUP plug-in for the generic
 * audit workflow (specs/backend/audit.md, specs/backend/spread.md). Covers CREATE/UPDATE/DELETE.
 */
@ExtendWith(MockitoExtension.class)
class SpreadGroupAuditHandlerTest {

    @Mock
    private SpreadGroupService spreadGroupService;

    @Mock
    private SpreadGroupValidator validator;

    @Mock
    private SpreadGroupMemberMapper spreadGroupMemberMapper;

    @Mock
    private CurrencyPairMapper currencyPairMapper;

    @Mock
    private AuditRequestMapper auditRequestMapper;

    private SpreadGroupAuditHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SpreadGroupAuditHandler(spreadGroupService, validator, spreadGroupMemberMapper,
                currencyPairMapper, auditRequestMapper);
    }

    private Brand sampleBrand() {
        Brand brand = new Brand();
        brand.setId(1L);
        brand.setCode("AU");
        brand.setName("AU");
        brand.setActive(true);
        return brand;
    }

    private CurrencyPair pair(Long id, String base, String quote) {
        CurrencyPair pair = new CurrencyPair();
        pair.setId(id);
        pair.setBrandId(1L);
        pair.setBaseCurrencyCode(base);
        pair.setQuoteCurrencyCode(quote);
        return pair;
    }

    private Map<String, Object> createAfter() {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("brandId", 1L);
        after.put("name", "Group A");
        after.put("depositSpread", new BigDecimal("0.1"));
        after.put("withdrawSpread", new BigDecimal("0.2"));
        after.put("currencyPairIds", List.of(3L, 4L));
        return after;
    }

    private void stubMembersLookup() {
        when(currencyPairMapper.findById(3L)).thenReturn(java.util.Optional.of(pair(3L, "USD", "JPY")));
        when(currencyPairMapper.findById(4L)).thenReturn(java.util.Optional.of(pair(4L, "USD", "EUR")));
    }

    private SpreadGroupResponse sampleGroupResponse() {
        SpreadGroupResponse response = new SpreadGroupResponse();
        response.setId(10L);
        response.setBrandId(1L);
        response.setBrandCode("AU");
        response.setName("Group A");
        response.setDepositSpread(new BigDecimal("0.1"));
        response.setWithdrawSpread(new BigDecimal("0.2"));
        response.setCreatedAt(LocalDateTime.now());
        response.setUpdatedAt(LocalDateTime.now());

        SpreadGroupMemberResponse m1 = new SpreadGroupMemberResponse();
        m1.setCurrencyPairId(3L);
        m1.setBaseCurrencyCode("USD");
        m1.setQuoteCurrencyCode("JPY");
        SpreadGroupMemberResponse m2 = new SpreadGroupMemberResponse();
        m2.setCurrencyPairId(4L);
        m2.setBaseCurrencyCode("USD");
        m2.setQuoteCurrencyCode("EUR");
        response.setMembers(List.of(m1, m2));
        return response;
    }

    // ---------- entityType ----------

    @Test
    void entityType_returnsSpreadGroup() {
        assertThat(handler.entityType()).isEqualTo("SPREAD_GROUP");
    }

    // ---------- snapshotOf ----------

    @Test
    void snapshotOf_returnsShapeMatchingSpec() {
        when(spreadGroupService.getById(10L)).thenReturn(sampleGroupResponse());

        Map<String, Object> snapshot = handler.snapshotOf(10L);

        assertThat(snapshot).containsEntry("brandId", 1L)
                .containsEntry("brandCode", "AU")
                .containsEntry("name", "Group A");
        assertThat(snapshot.get("currencyPairIds")).isEqualTo(List.of(3L, 4L));
        assertThat((List<?>) snapshot.get("members")).hasSize(2);
    }

    @Test
    void snapshotOf_propagatesNotFound_whenMissing() {
        when(spreadGroupService.getById(999L)).thenThrow(new SpreadGroupNotFoundException(999L));

        assertThatThrownBy(() -> handler.snapshotOf(999L)).isInstanceOf(SpreadGroupNotFoundException.class);
    }

    // ---------- validate: CREATE ----------

    @Test
    void validate_create_success_enrichesAfterWithBrandCodeAndMembers() {
        when(validator.requireBrandExists(1L)).thenReturn(sampleBrand());
        stubMembersLookup();
        when(auditRequestMapper.findAll("SPREAD_GROUP", "PENDING", "CREATE")).thenReturn(List.of());

        Map<String, Object> after = createAfter();
        handler.validate(AuditActionType.CREATE, null, after);

        assertThat(after).containsEntry("brandCode", "AU");
        assertThat((List<?>) after.get("members")).hasSize(2);
        verify(validator).requireNameUniqueWithinBrand(1L, "Group A", null);
        verify(validator).requireValidMembers(1L, List.of(3L, 4L));
    }

    @Test
    void validate_create_throwsBrandNotFound_whenBrandMissing() {
        when(validator.requireBrandExists(1L)).thenThrow(new BrandNotFoundException(1L));

        assertThatThrownBy(() -> handler.validate(AuditActionType.CREATE, null, createAfter()))
                .isInstanceOf(BrandNotFoundException.class);
    }

    @Test
    void validate_create_throwsNameExists_whenLiveDuplicate() {
        when(validator.requireBrandExists(1L)).thenReturn(sampleBrand());
        doThrow(new SpreadGroupNameExistsException(1L, "Group A"))
                .when(validator).requireNameUniqueWithinBrand(1L, "Group A", null);

        assertThatThrownBy(() -> handler.validate(AuditActionType.CREATE, null, createAfter()))
                .isInstanceOf(SpreadGroupNameExistsException.class);
    }

    @Test
    void validate_create_throwsInvalidSpread_whenNegativeSpread() {
        when(validator.requireBrandExists(1L)).thenReturn(sampleBrand());
        doThrow(new InvalidSpreadException("depositSpread is required and must be >= 0"))
                .when(validator).requireSpreadNonNegative(any(), any());

        assertThatThrownBy(() -> handler.validate(AuditActionType.CREATE, null, createAfter()))
                .isInstanceOf(InvalidSpreadException.class);
    }

    @Test
    void validate_create_throwsDuplicateMember_whenDuplicateIdsInPayload() {
        when(validator.requireBrandExists(1L)).thenReturn(sampleBrand());
        doThrow(new DuplicateSpreadGroupMemberException(3L))
                .when(validator).requireValidMembers(eq(1L), any());

        assertThatThrownBy(() -> handler.validate(AuditActionType.CREATE, null, createAfter()))
                .isInstanceOf(DuplicateSpreadGroupMemberException.class);
    }

    @Test
    void validate_create_throwsCurrencyPairNotFound_whenMemberIdUnknown() {
        when(validator.requireBrandExists(1L)).thenReturn(sampleBrand());
        doThrow(new CurrencyPairNotFoundException(999L))
                .when(validator).requireValidMembers(eq(1L), any());

        assertThatThrownBy(() -> handler.validate(AuditActionType.CREATE, null, createAfter()))
                .isInstanceOf(CurrencyPairNotFoundException.class);
    }

    @Test
    void validate_create_throwsInvalidMember_whenBrandMismatch() {
        when(validator.requireBrandExists(1L)).thenReturn(sampleBrand());
        doThrow(new InvalidSpreadGroupMemberException(3L, 1L))
                .when(validator).requireValidMembers(eq(1L), any());

        assertThatThrownBy(() -> handler.validate(AuditActionType.CREATE, null, createAfter()))
                .isInstanceOf(InvalidSpreadGroupMemberException.class);
    }

    @Test
    void validate_create_throwsDuplicatePendingCreate_whenPendingRequestExistsForSameBrandAndName() {
        when(validator.requireBrandExists(1L)).thenReturn(sampleBrand());
        stubMembersLookup();

        AuditRequest pending = new AuditRequest();
        pending.setAfterSnapshot("{\"brandId\":1,\"name\":\"Group A\"}");
        when(auditRequestMapper.findAll("SPREAD_GROUP", "PENDING", "CREATE")).thenReturn(List.of(pending));

        assertThatThrownBy(() -> handler.validate(AuditActionType.CREATE, null, createAfter()))
                .isInstanceOf(DuplicatePendingSpreadGroupCreateException.class);
    }

    @Test
    void validate_create_skipsPendingDuplicateCheck_whenSnapshotAlreadyEnriched_asAtApprovalTime() {
        when(validator.requireBrandExists(1L)).thenReturn(sampleBrand());
        stubMembersLookup();

        Map<String, Object> after = createAfter();
        after.put("brandCode", "AU"); // already enriched, as it would be when re-parsed at approval time
        after.put("members", List.of());

        handler.validate(AuditActionType.CREATE, null, after);

        verify(auditRequestMapper, never()).findAll(any(), any(), any());
    }

    // ---------- validate: UPDATE ----------

    @Test
    void validate_update_usesProvidedCurrencyPairIds_withoutLookingUpLiveMembership() {
        when(validator.requireBrandExists(1L)).thenReturn(sampleBrand());
        stubMembersLookup();

        Map<String, Object> after = createAfter();
        handler.validate(AuditActionType.UPDATE, 10L, after);

        verify(spreadGroupMemberMapper, never()).findByGroupId(any());
        verify(validator).requireNameUniqueWithinBrand(1L, "Group A", 10L);
    }

    @Test
    void validate_update_freezesLiveMembership_whenCurrencyPairIdsOmitted() {
        when(validator.requireBrandExists(1L)).thenReturn(sampleBrand());
        when(spreadGroupMemberMapper.findByGroupId(10L)).thenReturn(
                List.of(memberOf(3L), memberOf(4L)));
        stubMembersLookup();

        Map<String, Object> after = createAfter();
        after.remove("currencyPairIds");

        handler.validate(AuditActionType.UPDATE, 10L, after);

        verify(spreadGroupMemberMapper).findByGroupId(10L);
        assertThat(after.get("currencyPairIds")).isEqualTo(List.of(3L, 4L));
    }

    private SpreadGroupMember memberOf(Long currencyPairId) {
        SpreadGroupMember member = new SpreadGroupMember();
        member.setCurrencyPairId(currencyPairId);
        return member;
    }

    @Test
    void validate_update_neverRunsPendingCreateDedupCheck() {
        when(validator.requireBrandExists(1L)).thenReturn(sampleBrand());
        stubMembersLookup();

        handler.validate(AuditActionType.UPDATE, 10L, createAfter());

        verify(auditRequestMapper, never()).findAll(any(), any(), any());
    }

    // ---------- apply ----------

    @Test
    void apply_create_callsServiceCreate_andReturnsNewId() {
        Map<String, Object> after = createAfter();
        after.put("brandCode", "AU");
        when(spreadGroupService.create(eq(1L), eq("Group A"), eq(new BigDecimal("0.1")), eq(new BigDecimal("0.2")),
                eq(List.of(3L, 4L)))).thenReturn(sampleGroupResponse());

        Long resultId = handler.apply(AuditActionType.CREATE, null, after);

        assertThat(resultId).isEqualTo(10L);
    }

    @Test
    void apply_update_callsServiceUpdate_andReturnsEntityId() {
        Map<String, Object> after = createAfter();

        Long resultId = handler.apply(AuditActionType.UPDATE, 10L, after);

        assertThat(resultId).isEqualTo(10L);
        verify(spreadGroupService).update(eq(10L), eq("Group A"), eq(new BigDecimal("0.1")),
                eq(new BigDecimal("0.2")), eq(List.of(3L, 4L)));
    }

    @Test
    void apply_update_propagatesNotFound_whenGroupNoLongerExists() {
        doThrow(new SpreadGroupNotFoundException(10L))
                .when(spreadGroupService).update(eq(10L), any(), any(), any(), any());

        assertThatThrownBy(() -> handler.apply(AuditActionType.UPDATE, 10L, createAfter()))
                .isInstanceOf(SpreadGroupNotFoundException.class);
    }

    @Test
    void apply_delete_callsServiceDelete_andReturnsEntityId() {
        Long resultId = handler.apply(AuditActionType.DELETE, 10L, null);

        assertThat(resultId).isEqualTo(10L);
        verify(spreadGroupService).delete(10L);
    }

    // ---------- summarize ----------

    @Test
    void summarize_formatsBrandCodeAndName() {
        when(spreadGroupService.getById(10L)).thenReturn(sampleGroupResponse());
        Map<String, Object> snapshot = handler.snapshotOf(10L);

        String summary = handler.summarize(snapshot);

        assertThat(summary).isEqualTo("AU · Group A");
    }

    @Test
    void summarize_returnsEntityType_whenSnapshotNull() {
        assertThat(handler.summarize(null)).isEqualTo("SPREAD_GROUP");
    }
}
