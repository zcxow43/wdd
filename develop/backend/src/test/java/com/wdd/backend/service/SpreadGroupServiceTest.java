package com.wdd.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.wdd.backend.dto.SpreadGroupResponse;
import com.wdd.backend.dto.SpreadResolutionResponse;
import com.wdd.backend.exception.CurrencyPairNotFoundException;
import com.wdd.backend.exception.SpreadGroupNotFoundException;
import com.wdd.backend.mapper.CurrencyPairMapper;
import com.wdd.backend.mapper.SpreadDefaultMapper;
import com.wdd.backend.mapper.SpreadGroupMapper;
import com.wdd.backend.mapper.SpreadGroupMemberMapper;
import com.wdd.backend.model.CurrencyPair;
import com.wdd.backend.model.SpreadDefault;
import com.wdd.backend.model.SpreadGroup;
import com.wdd.backend.model.SpreadGroupMember;

@ExtendWith(MockitoExtension.class)
class SpreadGroupServiceTest {

    @Mock
    private SpreadGroupMapper spreadGroupMapper;

    @Mock
    private SpreadGroupMemberMapper spreadGroupMemberMapper;

    @Mock
    private CurrencyPairMapper currencyPairMapper;

    @Mock
    private SpreadDefaultMapper spreadDefaultMapper;

    private SpreadGroupService spreadGroupService;

    @BeforeEach
    void setUp() {
        spreadGroupService = new SpreadGroupService(spreadGroupMapper, spreadGroupMemberMapper, currencyPairMapper,
                spreadDefaultMapper);
    }

    private SpreadGroup sampleGroup() {
        SpreadGroup group = new SpreadGroup();
        group.setId(10L);
        group.setBrandId(1L);
        group.setBrandCode("AU");
        group.setName("Group A");
        group.setDepositSpread(new BigDecimal("0.1"));
        group.setWithdrawSpread(new BigDecimal("0.2"));
        group.setCreatedAt(LocalDateTime.now());
        group.setUpdatedAt(LocalDateTime.now());
        return group;
    }

    private SpreadGroupMember member(Long id, Long groupId, Long currencyPairId) {
        SpreadGroupMember member = new SpreadGroupMember();
        member.setId(id);
        member.setSpreadGroupId(groupId);
        member.setCurrencyPairId(currencyPairId);
        member.setBaseCurrencyCode("USD");
        member.setQuoteCurrencyCode("JPY");
        return member;
    }

    private CurrencyPair samplePair(Long id, Long brandId) {
        CurrencyPair pair = new CurrencyPair();
        pair.setId(id);
        pair.setBrandId(brandId);
        return pair;
    }

    // ---------- list / getById ----------

    @Test
    void list_returnsGroupsWithEnrichedMembers() {
        when(spreadGroupMapper.findAll(isNull())).thenReturn(List.of(sampleGroup()));
        when(spreadGroupMemberMapper.findByGroupId(10L)).thenReturn(List.of(member(1L, 10L, 3L)));

        List<SpreadGroupResponse> result = spreadGroupService.list(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMembers()).hasSize(1);
        assertThat(result.get(0).getMembers().get(0).getCurrencyPairId()).isEqualTo(3L);
    }

    @Test
    void getById_returnsGroupWithMembers() {
        when(spreadGroupMapper.findById(10L)).thenReturn(Optional.of(sampleGroup()));
        when(spreadGroupMemberMapper.findByGroupId(10L)).thenReturn(List.of());

        SpreadGroupResponse result = spreadGroupService.getById(10L);

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getMembers()).isEmpty();
    }

    @Test
    void getById_throwsNotFoundWhenMissing() {
        when(spreadGroupMapper.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> spreadGroupService.getById(999L))
                .isInstanceOf(SpreadGroupNotFoundException.class);
    }

    // ---------- create ----------

    @Test
    void create_insertsGroupAndAttachesMembers_detachingPriorMembership() {
        doAnswer(invocation -> {
            SpreadGroup group = invocation.getArgument(0);
            group.setId(10L);
            return 1;
        }).when(spreadGroupMapper).insert(any(SpreadGroup.class));
        when(spreadGroupMapper.findById(10L)).thenReturn(Optional.of(sampleGroup()));
        when(spreadGroupMemberMapper.findByGroupId(10L)).thenReturn(List.of(member(1L, 10L, 3L), member(2L, 10L, 4L)));

        SpreadGroupResponse result = spreadGroupService.create(1L, "Group A", new BigDecimal("0.1"),
                new BigDecimal("0.2"), List.of(3L, 4L));

        assertThat(result.getId()).isEqualTo(10L);
        verify(spreadGroupMapper).insert(any(SpreadGroup.class));
        verify(spreadGroupMemberMapper).deleteByCurrencyPairId(3L);
        verify(spreadGroupMemberMapper).deleteByCurrencyPairId(4L);
        verify(spreadGroupMemberMapper, times(2)).insert(any(SpreadGroupMember.class));
    }

    @Test
    void create_withNullCurrencyPairIds_insertsGroupWithNoMembers() {
        doAnswer(invocation -> {
            SpreadGroup group = invocation.getArgument(0);
            group.setId(11L);
            return 1;
        }).when(spreadGroupMapper).insert(any(SpreadGroup.class));
        SpreadGroup created = sampleGroup();
        created.setId(11L);
        when(spreadGroupMapper.findById(11L)).thenReturn(Optional.of(created));
        when(spreadGroupMemberMapper.findByGroupId(11L)).thenReturn(List.of());

        SpreadGroupResponse result = spreadGroupService.create(1L, "Group B", BigDecimal.ZERO, BigDecimal.ZERO, null);

        assertThat(result.getId()).isEqualTo(11L);
        verify(spreadGroupMemberMapper, never()).insert(any(SpreadGroupMember.class));
    }

    // ---------- update ----------

    @Test
    void update_persistsFieldsAndLeavesMembershipUnchanged_whenCurrencyPairIdsNull() {
        when(spreadGroupMapper.findById(10L)).thenReturn(Optional.of(sampleGroup()));
        when(spreadGroupMemberMapper.findByGroupId(10L)).thenReturn(List.of());

        spreadGroupService.update(10L, "Renamed", new BigDecimal("0.15"), new BigDecimal("0.25"), null);

        verify(spreadGroupMapper).update(any(SpreadGroup.class));
        // findByGroupId is still called once, but only by getById's response-building step below
        // — never as part of the (skipped) membership-diff, since currencyPairIds is null.
        verify(spreadGroupMemberMapper, times(1)).findByGroupId(10L);
        verify(spreadGroupMemberMapper, never()).deleteByCurrencyPairId(any());
    }

    @Test
    void update_replacesMembership_removingAbsentAddingNewLeavingUnchangedAlone() {
        when(spreadGroupMapper.findById(10L)).thenReturn(Optional.of(sampleGroup()));
        // Current membership: 3, 4. Desired: 3, 5 -> remove 4, add 5 (detach-elsewhere), leave 3.
        when(spreadGroupMemberMapper.findByGroupId(10L)).thenReturn(List.of(member(1L, 10L, 3L), member(2L, 10L, 4L)));

        spreadGroupService.update(10L, "Group A", new BigDecimal("0.1"), new BigDecimal("0.2"), List.of(3L, 5L));

        verify(spreadGroupMemberMapper).deleteByCurrencyPairId(4L);
        verify(spreadGroupMemberMapper).deleteByCurrencyPairId(5L);
        verify(spreadGroupMemberMapper, never()).deleteByCurrencyPairId(3L);
        verify(spreadGroupMemberMapper).insert(any(SpreadGroupMember.class));
    }

    @Test
    void update_throwsNotFoundWhenMissing() {
        when(spreadGroupMapper.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> spreadGroupService.update(999L, "X", BigDecimal.ZERO, BigDecimal.ZERO, null))
                .isInstanceOf(SpreadGroupNotFoundException.class);
    }

    // ---------- delete ----------

    @Test
    void delete_removesMembershipsThenGroup() {
        when(spreadGroupMapper.findById(10L)).thenReturn(Optional.of(sampleGroup()));

        spreadGroupService.delete(10L);

        verify(spreadGroupMemberMapper).deleteByGroupId(10L);
        verify(spreadGroupMapper).deleteById(10L);
    }

    @Test
    void delete_throwsNotFoundWhenMissing() {
        when(spreadGroupMapper.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> spreadGroupService.delete(999L))
                .isInstanceOf(SpreadGroupNotFoundException.class);

        verify(spreadGroupMemberMapper, never()).deleteByGroupId(any());
        verify(spreadGroupMapper, never()).deleteById(any());
    }

    // ---------- resolveEffectiveSpread ----------

    @Test
    void resolveEffectiveSpread_returnsGroupSpread_whenPairIsAMember() {
        when(currencyPairMapper.findById(3L)).thenReturn(Optional.of(samplePair(3L, 1L)));
        when(spreadGroupMemberMapper.findByCurrencyPairId(3L)).thenReturn(Optional.of(member(1L, 10L, 3L)));
        when(spreadGroupMapper.findById(10L)).thenReturn(Optional.of(sampleGroup()));

        SpreadResolutionResponse result = spreadGroupService.resolveEffectiveSpread(3L);

        assertThat(result.getSource()).isEqualTo("GROUP");
        assertThat(result.getSpreadGroupId()).isEqualTo(10L);
        assertThat(result.getSpreadGroupName()).isEqualTo("Group A");
        assertThat(result.getDepositSpread()).isEqualByComparingTo("0.1");
        assertThat(result.getWithdrawSpread()).isEqualByComparingTo("0.2");
    }

    @Test
    void resolveEffectiveSpread_returnsDefaultSpread_whenPairHasNoGroup() {
        when(currencyPairMapper.findById(4L)).thenReturn(Optional.of(samplePair(4L, 1L)));
        when(spreadGroupMemberMapper.findByCurrencyPairId(4L)).thenReturn(Optional.empty());
        SpreadDefault spreadDefault = new SpreadDefault();
        spreadDefault.setBrandId(1L);
        spreadDefault.setDepositSpread(new BigDecimal("0.5"));
        spreadDefault.setWithdrawSpread(new BigDecimal("0.6"));
        when(spreadDefaultMapper.findByBrandId(1L)).thenReturn(Optional.of(spreadDefault));

        SpreadResolutionResponse result = spreadGroupService.resolveEffectiveSpread(4L);

        assertThat(result.getSource()).isEqualTo("DEFAULT");
        assertThat(result.getSpreadGroupId()).isNull();
        assertThat(result.getSpreadGroupName()).isNull();
        assertThat(result.getDepositSpread()).isEqualByComparingTo("0.5");
        assertThat(result.getWithdrawSpread()).isEqualByComparingTo("0.6");
    }

    @Test
    void resolveEffectiveSpread_throwsNotFound_whenCurrencyPairMissing() {
        when(currencyPairMapper.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> spreadGroupService.resolveEffectiveSpread(999L))
                .isInstanceOf(CurrencyPairNotFoundException.class);
    }
}
