package com.wdd.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wdd.backend.dto.AuditPendingResponse;
import com.wdd.backend.dto.AuditRequest;
import com.wdd.backend.dto.Brand;
import com.wdd.backend.dto.CurrencyPair;
import com.wdd.backend.dto.SpreadGroup;
import com.wdd.backend.dto.SpreadGroupCreateRequest;
import com.wdd.backend.dto.SpreadGroupMemberAssignRequest;
import com.wdd.backend.dto.SpreadGroupUpdateRequest;
import com.wdd.backend.exception.CurrencyPairBrandMismatchException;
import com.wdd.backend.exception.InvalidRequestException;
import com.wdd.backend.exception.SpreadGroupMemberConflictException;
import com.wdd.backend.exception.SpreadGroupMemberNotFoundException;
import com.wdd.backend.exception.SpreadGroupNameConflictException;
import com.wdd.backend.exception.SpreadGroupNotFoundException;
import com.wdd.backend.exception.UnknownCurrencyPairIdsException;
import com.wdd.backend.mapper.BrandMapper;
import com.wdd.backend.mapper.CurrencyPairMapper;
import com.wdd.backend.mapper.SpreadGroupMapper;

class SpreadGroupServiceTest {

    private SpreadGroupMapper spreadGroupMapper;
    private CurrencyPairMapper currencyPairMapper;
    private BrandMapper brandMapper;
    private AuditService auditService;
    private SpreadGroupService service;

    @BeforeEach
    void setUp() {
        spreadGroupMapper = mock(SpreadGroupMapper.class);
        currencyPairMapper = mock(CurrencyPairMapper.class);
        brandMapper = mock(BrandMapper.class);
        auditService = mock(AuditService.class);
        service = new SpreadGroupService(spreadGroupMapper, currencyPairMapper, brandMapper, auditService);

        when(auditService.submit(anyString(), anyString(), any(), any(), anyString(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    AuditRequest request = new AuditRequest();
                    request.setId(999L);
                    request.setEntityType(invocation.getArgument(0));
                    request.setActionType(invocation.getArgument(1));
                    request.setEntityId(invocation.getArgument(2));
                    request.setBrandId(invocation.getArgument(3));
                    request.setSummary(invocation.getArgument(4));
                    request.setBeforeData(invocation.getArgument(5));
                    request.setAfterData(invocation.getArgument(6));
                    request.setStatus("PENDING");
                    return request;
                });
    }

    private static Brand sampleBrand(Long id, String code) {
        Brand brand = new Brand();
        brand.setId(id);
        brand.setCode(code);
        brand.setName(code);
        brand.setActive(true);
        return brand;
    }

    private static SpreadGroup sampleGroup(Long id, Long brandId, String name, BigDecimal deposit,
            BigDecimal withdrawal, Integer memberCount) {
        SpreadGroup group = new SpreadGroup();
        group.setId(id);
        group.setBrandId(brandId);
        group.setBrandCode("au");
        group.setName(name);
        group.setDepositSpread(deposit);
        group.setWithdrawalSpread(withdrawal);
        group.setMemberCount(memberCount);
        group.setCreatedAt(LocalDateTime.now());
        group.setUpdatedAt(LocalDateTime.now());
        return group;
    }

    private static CurrencyPair samplePair(Long id, Long brandId, Long groupId, String groupName) {
        CurrencyPair pair = new CurrencyPair();
        pair.setId(id);
        pair.setCurrencyPairDefinitionId(1L);
        pair.setBaseCurrencyCode("USD");
        pair.setQuoteCurrencyCode("JPY");
        pair.setBrandId(brandId);
        pair.setBrandCode("au");
        pair.setRateType("AUTO");
        pair.setActive(true);
        pair.setSpreadGroupId(groupId);
        pair.setSpreadGroupName(groupName);
        return pair;
    }

    // --- create ---

    @Test
    void createRejectsMissingBrandId() {
        SpreadGroupCreateRequest request = new SpreadGroupCreateRequest(null, "VIP", null, null);

        assertThatThrownBy(() -> service.create(request, null))
                .isInstanceOf(InvalidRequestException.class);
        verify(auditService, never()).submit(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void createRejectsUnknownBrand() {
        when(brandMapper.findById(1L)).thenReturn(null);
        SpreadGroupCreateRequest request = new SpreadGroupCreateRequest(1L, "VIP", null, null);

        assertThatThrownBy(() -> service.create(request, null))
                .isInstanceOf(InvalidRequestException.class);
        verify(auditService, never()).submit(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void createRejectsBlankName() {
        when(brandMapper.findById(1L)).thenReturn(sampleBrand(1L, "au"));
        SpreadGroupCreateRequest request = new SpreadGroupCreateRequest(1L, "   ", null, null);

        assertThatThrownBy(() -> service.create(request, null))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void createRejectsNameOver50Characters() {
        when(brandMapper.findById(1L)).thenReturn(sampleBrand(1L, "au"));
        String longName = "a".repeat(51);
        SpreadGroupCreateRequest request = new SpreadGroupCreateRequest(1L, longName, null, null);

        assertThatThrownBy(() -> service.create(request, null))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void createDefaultsSpreadsToZeroAndSubmitsAuditRequestWithNullEntityId() {
        when(brandMapper.findById(1L)).thenReturn(sampleBrand(1L, "au"));
        when(spreadGroupMapper.findByBrandAndName(1L, "VIP")).thenReturn(null);

        SpreadGroupCreateRequest request = new SpreadGroupCreateRequest(1L, "VIP", null, null);
        AuditPendingResponse response = service.create(request, "alice");

        assertThat(response.getAuditRequestId()).isEqualTo(999L);
        assertThat(response.getEntityType()).isEqualTo("SPREAD_GROUP");
        assertThat(response.getActionType()).isEqualTo("CREATE");
        assertThat(response.getEntityId()).isNull();

        org.mockito.ArgumentCaptor<Object> afterCaptor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(auditService).submit(eq("SPREAD_GROUP"), eq("CREATE"), isNull(), eq(1L), anyString(), isNull(),
                afterCaptor.capture(), eq("alice"));
        @SuppressWarnings("unchecked")
        Map<String, Object> after = (Map<String, Object>) afterCaptor.getValue();
        assertThat(after.get("name")).isEqualTo("VIP");
        assertThat((BigDecimal) after.get("depositSpread")).isEqualByComparingTo("0");
        assertThat((BigDecimal) after.get("withdrawalSpread")).isEqualByComparingTo("0");
        verify(spreadGroupMapper, never()).insert(any());
    }

    @Test
    void createRejectsDuplicateNameForSameBrand() {
        when(brandMapper.findById(1L)).thenReturn(sampleBrand(1L, "au"));
        when(spreadGroupMapper.findByBrandAndName(1L, "VIP"))
                .thenReturn(sampleGroup(5L, 1L, "VIP", BigDecimal.ZERO, BigDecimal.ZERO, 0));
        SpreadGroupCreateRequest request = new SpreadGroupCreateRequest(1L, "VIP", null, null);

        assertThatThrownBy(() -> service.create(request, null))
                .isInstanceOf(SpreadGroupNameConflictException.class);
        verify(spreadGroupMapper, never()).insert(any());
        verify(auditService, never()).submit(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void createRejectsNegativeOrOverPrecisionSpread() {
        when(brandMapper.findById(1L)).thenReturn(sampleBrand(1L, "au"));
        when(spreadGroupMapper.findByBrandAndName(1L, "VIP")).thenReturn(null);

        SpreadGroupCreateRequest negative = new SpreadGroupCreateRequest(1L, "VIP", new BigDecimal("-1"), null);
        assertThatThrownBy(() -> service.create(negative, null)).isInstanceOf(InvalidRequestException.class);

        SpreadGroupCreateRequest overPrecision = new SpreadGroupCreateRequest(1L, "VIP", null,
                new BigDecimal("0.000000001"));
        assertThatThrownBy(() -> service.create(overPrecision, null)).isInstanceOf(InvalidRequestException.class);
    }

    // --- update ---

    @Test
    void updateThrowsWhenGroupMissing() {
        when(spreadGroupMapper.findById(999L)).thenReturn(null);
        SpreadGroupUpdateRequest request = new SpreadGroupUpdateRequest("VIP+", null, null);

        assertThatThrownBy(() -> service.update(999L, request, null))
                .isInstanceOf(SpreadGroupNotFoundException.class);
        verify(auditService, never()).submit(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void updateIgnoresBrandIdAndKeepsUnsentFieldsUnchangedInAfterData() {
        SpreadGroup existing = sampleGroup(10L, 1L, "VIP", new BigDecimal("0.0002"), new BigDecimal("0.0003"), 2);
        when(spreadGroupMapper.findById(10L)).thenReturn(existing);
        SpreadGroupUpdateRequest request = new SpreadGroupUpdateRequest("VIP+", null, null);

        AuditPendingResponse response = service.update(10L, request, null);

        assertThat(response.getEntityType()).isEqualTo("SPREAD_GROUP");
        assertThat(response.getActionType()).isEqualTo("UPDATE");
        assertThat(response.getEntityId()).isEqualTo(10L);

        org.mockito.ArgumentCaptor<Object> afterCaptor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(auditService).submit(eq("SPREAD_GROUP"), eq("UPDATE"), eq(10L), eq(1L), anyString(), any(),
                afterCaptor.capture(), isNull());
        @SuppressWarnings("unchecked")
        Map<String, Object> after = (Map<String, Object>) afterCaptor.getValue();
        assertThat(after.get("name")).isEqualTo("VIP+");
        assertThat((BigDecimal) after.get("depositSpread")).isEqualByComparingTo("0.0002");
        assertThat((BigDecimal) after.get("withdrawalSpread")).isEqualByComparingTo("0.0003");
        verify(spreadGroupMapper, never()).update(any());
    }

    @Test
    void updateRejectsNameCollisionWithAnotherGroupInSameBrand() {
        SpreadGroup existing = sampleGroup(10L, 1L, "VIP", BigDecimal.ZERO, BigDecimal.ZERO, 0);
        when(spreadGroupMapper.findById(10L)).thenReturn(existing);
        when(spreadGroupMapper.findByBrandAndName(1L, "STD"))
                .thenReturn(sampleGroup(11L, 1L, "STD", BigDecimal.ZERO, BigDecimal.ZERO, 0));
        SpreadGroupUpdateRequest request = new SpreadGroupUpdateRequest("STD", null, null);

        assertThatThrownBy(() -> service.update(10L, request, null))
                .isInstanceOf(SpreadGroupNameConflictException.class);
        verify(spreadGroupMapper, never()).update(any());
        verify(auditService, never()).submit(any(), any(), any(), any(), any(), any(), any(), any());
    }

    // --- delete ---

    @Test
    void deleteThrowsWhenGroupMissing() {
        when(spreadGroupMapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.delete(999L, null))
                .isInstanceOf(SpreadGroupNotFoundException.class);
        verify(spreadGroupMapper, never()).deleteById(any());
        verify(auditService, never()).submit(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void deleteSubmitsAuditRequestAndDoesNotWriteDirectly() {
        when(spreadGroupMapper.findById(10L)).thenReturn(sampleGroup(10L, 1L, "VIP", BigDecimal.ZERO,
                BigDecimal.ZERO, 3));

        AuditPendingResponse response = service.delete(10L, null);

        assertThat(response.getActionType()).isEqualTo("DELETE");
        assertThat(response.getEntityId()).isEqualTo(10L);
        verify(auditService).submit(eq("SPREAD_GROUP"), eq("DELETE"), eq(10L), eq(1L), anyString(), any(), isNull(),
                isNull());
        verify(spreadGroupMapper, never()).deleteById(any());
        verify(currencyPairMapper, never()).clearSpreadGroupIfMember(any(), any());
        verify(currencyPairMapper, never()).updateSpreadGroupForIds(any(), any());
    }

    // --- assignMembers ---

    @Test
    void assignMembersThrowsWhenGroupMissing() {
        when(spreadGroupMapper.findById(999L)).thenReturn(null);
        SpreadGroupMemberAssignRequest request = new SpreadGroupMemberAssignRequest(List.of(1L));

        assertThatThrownBy(() -> service.assignMembers(999L, request, null))
                .isInstanceOf(SpreadGroupNotFoundException.class);
    }

    @Test
    void assignMembersRejectsEmptyOrMissingIdList() {
        when(spreadGroupMapper.findById(10L)).thenReturn(sampleGroup(10L, 1L, "VIP", BigDecimal.ZERO,
                BigDecimal.ZERO, 0));

        assertThatThrownBy(() -> service.assignMembers(10L, new SpreadGroupMemberAssignRequest(List.of()), null))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.assignMembers(10L, null, null))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void assignMembersRejectsUnknownIdsAndSubmitsNoRequest() {
        when(spreadGroupMapper.findById(10L)).thenReturn(sampleGroup(10L, 1L, "VIP", BigDecimal.ZERO,
                BigDecimal.ZERO, 0));
        when(currencyPairMapper.findByIds(List.of(1L, 2L))).thenReturn(List.of(samplePair(1L, 1L, null, null)));

        SpreadGroupMemberAssignRequest request = new SpreadGroupMemberAssignRequest(List.of(1L, 2L));

        assertThatThrownBy(() -> service.assignMembers(10L, request, null))
                .isInstanceOf(UnknownCurrencyPairIdsException.class)
                .satisfies(ex -> assertThat(((UnknownCurrencyPairIdsException) ex).getCurrencyPairIds())
                        .containsExactly(2L));
        verify(auditService, never()).submit(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void assignMembersRejectsBrandMismatchAndSubmitsNoRequest() {
        when(spreadGroupMapper.findById(10L)).thenReturn(sampleGroup(10L, 1L, "VIP", BigDecimal.ZERO,
                BigDecimal.ZERO, 0));
        when(currencyPairMapper.findByIds(List.of(1L, 2L))).thenReturn(List.of(
                samplePair(1L, 1L, null, null),
                samplePair(2L, 2L, null, null)));

        SpreadGroupMemberAssignRequest request = new SpreadGroupMemberAssignRequest(List.of(1L, 2L));

        assertThatThrownBy(() -> service.assignMembers(10L, request, null))
                .isInstanceOf(CurrencyPairBrandMismatchException.class)
                .satisfies(ex -> assertThat(((CurrencyPairBrandMismatchException) ex).getCurrencyPairIds())
                        .containsExactly(2L));
        verify(auditService, never()).submit(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void assignMembersRejectsPairAlreadyInDifferentGroupAndSubmitsNoRequest() {
        when(spreadGroupMapper.findById(10L)).thenReturn(sampleGroup(10L, 1L, "VIP", BigDecimal.ZERO,
                BigDecimal.ZERO, 0));
        when(currencyPairMapper.findByIds(List.of(1L, 2L))).thenReturn(List.of(
                samplePair(1L, 1L, null, null),
                samplePair(2L, 1L, 2L, "STD")));

        SpreadGroupMemberAssignRequest request = new SpreadGroupMemberAssignRequest(List.of(1L, 2L));

        assertThatThrownBy(() -> service.assignMembers(10L, request, null))
                .isInstanceOf(SpreadGroupMemberConflictException.class)
                .satisfies(ex -> {
                    List<java.util.Map<String, Object>> conflicts =
                            ((SpreadGroupMemberConflictException) ex).getConflicts();
                    assertThat(conflicts).hasSize(1);
                    assertThat(conflicts.get(0).get("currencyPairId")).isEqualTo(2L);
                    assertThat(conflicts.get(0).get("spreadGroupId")).isEqualTo(2L);
                    assertThat(conflicts.get(0).get("spreadGroupName")).isEqualTo("STD");
                });
        verify(auditService, never()).submit(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void assignMembersSubmitsAuditRequestWithOperationAddAndFullIdList() {
        when(spreadGroupMapper.findById(10L))
                .thenReturn(sampleGroup(10L, 1L, "VIP", BigDecimal.ZERO, BigDecimal.ZERO, 1));
        when(currencyPairMapper.findByIds(List.of(1L, 2L))).thenReturn(List.of(
                samplePair(1L, 1L, 10L, "VIP"),
                samplePair(2L, 1L, null, null)));
        when(currencyPairMapper.findBySpreadGroupId(10L)).thenReturn(List.of(samplePair(1L, 1L, 10L, "VIP")));

        SpreadGroupMemberAssignRequest request = new SpreadGroupMemberAssignRequest(List.of(1L, 2L));
        AuditPendingResponse response = service.assignMembers(10L, request, "bob");

        assertThat(response.getEntityType()).isEqualTo("SPREAD_GROUP_MEMBER");
        assertThat(response.getActionType()).isEqualTo("UPDATE");
        assertThat(response.getEntityId()).isEqualTo(10L);

        org.mockito.ArgumentCaptor<Object> afterCaptor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(auditService).submit(eq("SPREAD_GROUP_MEMBER"), eq("UPDATE"), eq(10L), eq(1L), anyString(), any(),
                afterCaptor.capture(), eq("bob"));
        @SuppressWarnings("unchecked")
        Map<String, Object> after = (Map<String, Object>) afterCaptor.getValue();
        assertThat(after.get("operation")).isEqualTo("ADD");
        assertThat(after.get("currencyPairIds")).isEqualTo(List.of(1L, 2L));
        verify(currencyPairMapper, never()).updateSpreadGroupForIds(any(), any());
    }

    @Test
    void assignMembersAllowsAlreadyInThisGroupPairsAsNoOpWithinBatch() {
        when(spreadGroupMapper.findById(10L))
                .thenReturn(sampleGroup(10L, 1L, "VIP", BigDecimal.ZERO, BigDecimal.ZERO, 1));
        when(currencyPairMapper.findByIds(List.of(1L))).thenReturn(List.of(samplePair(1L, 1L, 10L, "VIP")));
        when(currencyPairMapper.findBySpreadGroupId(10L)).thenReturn(List.of(samplePair(1L, 1L, 10L, "VIP")));

        SpreadGroupMemberAssignRequest request = new SpreadGroupMemberAssignRequest(List.of(1L));
        AuditPendingResponse response = service.assignMembers(10L, request, null);

        assertThat(response.getActionType()).isEqualTo("UPDATE");
        verify(currencyPairMapper, never()).updateSpreadGroupForIds(any(), any());
    }

    // --- removeMember ---

    @Test
    void removeMemberThrowsWhenGroupMissing() {
        when(spreadGroupMapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.removeMember(999L, 1L, null))
                .isInstanceOf(SpreadGroupNotFoundException.class);
    }

    @Test
    void removeMemberThrowsWhenPairNotAMember() {
        when(spreadGroupMapper.findById(10L)).thenReturn(sampleGroup(10L, 1L, "VIP", BigDecimal.ZERO,
                BigDecimal.ZERO, 0));
        when(currencyPairMapper.findById(1L)).thenReturn(samplePair(1L, 1L, null, null));

        assertThatThrownBy(() -> service.removeMember(10L, 1L, null))
                .isInstanceOf(SpreadGroupMemberNotFoundException.class);
        verify(auditService, never()).submit(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void removeMemberThrowsWhenPairBelongsToDifferentGroup() {
        when(spreadGroupMapper.findById(10L)).thenReturn(sampleGroup(10L, 1L, "VIP", BigDecimal.ZERO,
                BigDecimal.ZERO, 0));
        when(currencyPairMapper.findById(1L)).thenReturn(samplePair(1L, 1L, 2L, "STD"));

        assertThatThrownBy(() -> service.removeMember(10L, 1L, null))
                .isInstanceOf(SpreadGroupMemberNotFoundException.class);
    }

    @Test
    void removeMemberSubmitsAuditRequestWithOperationRemoveAndDoesNotWrite() {
        when(spreadGroupMapper.findById(10L)).thenReturn(sampleGroup(10L, 1L, "VIP", BigDecimal.ZERO,
                BigDecimal.ZERO, 1));
        when(currencyPairMapper.findById(1L)).thenReturn(samplePair(1L, 1L, 10L, "VIP"));

        AuditPendingResponse response = service.removeMember(10L, 1L, null);

        assertThat(response.getEntityType()).isEqualTo("SPREAD_GROUP_MEMBER");
        assertThat(response.getActionType()).isEqualTo("UPDATE");
        assertThat(response.getEntityId()).isEqualTo(10L);

        org.mockito.ArgumentCaptor<Object> afterCaptor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(auditService).submit(eq("SPREAD_GROUP_MEMBER"), eq("UPDATE"), eq(10L), eq(1L), anyString(), any(),
                afterCaptor.capture(), isNull());
        @SuppressWarnings("unchecked")
        Map<String, Object> after = (Map<String, Object>) afterCaptor.getValue();
        assertThat(after.get("operation")).isEqualTo("REMOVE");
        assertThat(after.get("currencyPairIds")).isEqualTo(List.of(1L));
        verify(currencyPairMapper, never()).clearSpreadGroupIfMember(any(), any());
    }
}
