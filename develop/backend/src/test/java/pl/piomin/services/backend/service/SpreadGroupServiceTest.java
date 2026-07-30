package pl.piomin.services.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import pl.piomin.services.backend.dto.SpreadResolutionResponse;
import pl.piomin.services.backend.exception.CurrencyPairNotFoundException;
import pl.piomin.services.backend.exception.SpreadGroupNotFoundException;
import pl.piomin.services.backend.mapper.CurrencyPairMapper;
import pl.piomin.services.backend.mapper.SpreadDefaultMapper;
import pl.piomin.services.backend.mapper.SpreadGroupMapper;
import pl.piomin.services.backend.mapper.SpreadGroupMemberMapper;
import pl.piomin.services.backend.model.CurrencyPair;
import pl.piomin.services.backend.model.SpreadDefault;
import pl.piomin.services.backend.model.SpreadGroup;
import pl.piomin.services.backend.model.SpreadGroupMember;

@ExtendWith(MockitoExtension.class)
class SpreadGroupServiceTest {

    @Mock
    private SpreadGroupMapper spreadGroupMapper;

    @Mock
    private SpreadGroupMemberMapper spreadGroupMemberMapper;

    @Mock
    private SpreadDefaultMapper spreadDefaultMapper;

    @Mock
    private CurrencyPairMapper currencyPairMapper;

    private SpreadGroupService spreadGroupService;

    @BeforeEach
    void setUp() {
        spreadGroupService = new SpreadGroupService(spreadGroupMapper, spreadGroupMemberMapper, spreadDefaultMapper,
                currencyPairMapper);
    }

    private SpreadGroup sampleGroup(Long id, Long brandId, String name) {
        SpreadGroup group = new SpreadGroup();
        group.setId(id);
        group.setBrandId(brandId);
        group.setBrandCode("AU");
        group.setName(name);
        group.setDepositSpread(new BigDecimal("0.1"));
        group.setWithdrawSpread(new BigDecimal("0.2"));
        return group;
    }

    private SpreadGroupMember sampleMember(Long id, Long groupId, Long pairId) {
        SpreadGroupMember member = new SpreadGroupMember();
        member.setId(id);
        member.setSpreadGroupId(groupId);
        member.setCurrencyPairId(pairId);
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

    @Test
    void list_returnsGroupsFromMapper() {
        when(spreadGroupMapper.findAll(null)).thenReturn(List.of(sampleGroup(1L, 1L, "Group A")));

        assertThat(spreadGroupService.list(null)).hasSize(1);
    }

    @Test
    void getById_returnsGroup_whenFound() {
        when(spreadGroupMapper.findById(1L)).thenReturn(sampleGroup(1L, 1L, "Group A"));

        assertThat(spreadGroupService.getById(1L).getName()).isEqualTo("Group A");
    }

    @Test
    void getById_throwsNotFound_whenMissing() {
        when(spreadGroupMapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> spreadGroupService.getById(999L))
                .isInstanceOf(SpreadGroupNotFoundException.class);
    }

    @Test
    void getMembers_returnsMembersFromMapper() {
        when(spreadGroupMemberMapper.findByGroupId(1L)).thenReturn(List.of(sampleMember(1L, 1L, 3L)));

        assertThat(spreadGroupService.getMembers(1L)).hasSize(1);
    }

    @Test
    void create_insertsGroupAndMembers_detachingPriorMembership() {
        doAnswer(invocation -> {
            SpreadGroup toInsert = invocation.getArgument(0);
            toInsert.setId(10L);
            return 1;
        }).when(spreadGroupMapper).insert(any(SpreadGroup.class));
        when(spreadGroupMapper.findById(10L)).thenReturn(sampleGroup(10L, 1L, "Group A"));

        SpreadGroup result = spreadGroupService.create(1L, "Group A", new BigDecimal("0.1"), new BigDecimal("0.2"),
                List.of(3L, 4L));

        assertThat(result.getId()).isEqualTo(10L);
        verify(spreadGroupMemberMapper).deleteByCurrencyPairId(3L);
        verify(spreadGroupMemberMapper).deleteByCurrencyPairId(4L);
        verify(spreadGroupMemberMapper, times(2)).insert(any(SpreadGroupMember.class));
    }

    @Test
    void create_insertsGroupWithNoMembers_whenListEmpty() {
        doAnswer(invocation -> {
            SpreadGroup toInsert = invocation.getArgument(0);
            toInsert.setId(11L);
            return 1;
        }).when(spreadGroupMapper).insert(any(SpreadGroup.class));
        when(spreadGroupMapper.findById(11L)).thenReturn(sampleGroup(11L, 1L, "Empty Group"));

        spreadGroupService.create(1L, "Empty Group", BigDecimal.ZERO, BigDecimal.ZERO, List.of());

        verify(spreadGroupMemberMapper, never()).insert(any(SpreadGroupMember.class));
    }

    @Test
    void update_throwsNotFound_whenMissing() {
        when(spreadGroupMapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> spreadGroupService.update(999L, "New Name", BigDecimal.ZERO, BigDecimal.ZERO, null))
                .isInstanceOf(SpreadGroupNotFoundException.class);
    }

    @Test
    void update_persistsFields_andLeavesMembershipUnchanged_whenCurrencyPairIdsNull() {
        SpreadGroup existing = sampleGroup(10L, 1L, "Group A");
        when(spreadGroupMapper.findById(10L)).thenReturn(existing);

        spreadGroupService.update(10L, "Renamed", new BigDecimal("0.5"), new BigDecimal("0.6"), null);

        verify(spreadGroupMapper).update(existing);
        assertThat(existing.getName()).isEqualTo("Renamed");
        verify(spreadGroupMemberMapper, never()).findByGroupId(10L);
        verify(spreadGroupMemberMapper, never()).deleteByCurrencyPairId(any());
        verify(spreadGroupMemberMapper, never()).insert(any(SpreadGroupMember.class));
    }

    @Test
    void update_replacesMembership_removingAbsent_addingNew_leavingUnchangedAlone() {
        SpreadGroup existing = sampleGroup(10L, 1L, "Group A");
        when(spreadGroupMapper.findById(10L)).thenReturn(existing);
        when(spreadGroupMemberMapper.findByGroupId(10L))
                .thenReturn(List.of(sampleMember(1L, 10L, 3L), sampleMember(2L, 10L, 4L)));

        // desired: keep 3, drop 4, add 5
        spreadGroupService.update(10L, "Group A", new BigDecimal("0.1"), new BigDecimal("0.2"), List.of(3L, 5L));

        verify(spreadGroupMemberMapper).deleteByCurrencyPairId(4L); // removed (reverts to default)
        verify(spreadGroupMemberMapper).deleteByCurrencyPairId(5L); // detach-elsewhere before insert
        verify(spreadGroupMemberMapper, never()).deleteByCurrencyPairId(3L); // unchanged pair left alone
        verify(spreadGroupMemberMapper, times(1)).insert(any(SpreadGroupMember.class));
    }

    @Test
    void delete_removesMembersThenGroup_whenFound() {
        when(spreadGroupMapper.findById(10L)).thenReturn(sampleGroup(10L, 1L, "Group A"));

        spreadGroupService.delete(10L);

        verify(spreadGroupMemberMapper).deleteByGroupId(10L);
        verify(spreadGroupMapper).deleteById(10L);
    }

    @Test
    void delete_throwsNotFound_whenMissing() {
        when(spreadGroupMapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> spreadGroupService.delete(999L))
                .isInstanceOf(SpreadGroupNotFoundException.class);
        verify(spreadGroupMapper, never()).deleteById(any());
    }

    @Test
    void resolveEffectiveSpread_returnsGroupSource_whenPairIsMember() {
        when(currencyPairMapper.findById(3L)).thenReturn(samplePair(3L, 1L));
        when(spreadGroupMemberMapper.findByCurrencyPairId(3L)).thenReturn(sampleMember(1L, 10L, 3L));
        when(spreadGroupMapper.findById(10L)).thenReturn(sampleGroup(10L, 1L, "Group A"));

        SpreadResolutionResponse result = spreadGroupService.resolveEffectiveSpread(3L);

        assertThat(result.getSource()).isEqualTo("GROUP");
        assertThat(result.getSpreadGroupId()).isEqualTo(10L);
        assertThat(result.getSpreadGroupName()).isEqualTo("Group A");
        assertThat(result.getDepositSpread()).isEqualByComparingTo("0.1");
    }

    @Test
    void resolveEffectiveSpread_returnsDefaultSource_whenPairHasNoGroup() {
        when(currencyPairMapper.findById(3L)).thenReturn(samplePair(3L, 1L));
        when(spreadGroupMemberMapper.findByCurrencyPairId(3L)).thenReturn(null);
        SpreadDefault spreadDefault = new SpreadDefault();
        spreadDefault.setDepositSpread(new BigDecimal("0.05"));
        spreadDefault.setWithdrawSpread(new BigDecimal("0.07"));
        when(spreadDefaultMapper.findByBrandId(1L)).thenReturn(spreadDefault);

        SpreadResolutionResponse result = spreadGroupService.resolveEffectiveSpread(3L);

        assertThat(result.getSource()).isEqualTo("DEFAULT");
        assertThat(result.getSpreadGroupId()).isNull();
        assertThat(result.getDepositSpread()).isEqualByComparingTo("0.05");
        assertThat(result.getWithdrawSpread()).isEqualByComparingTo("0.07");
    }

    @Test
    void resolveEffectiveSpread_throwsNotFound_whenPairMissing() {
        when(currencyPairMapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> spreadGroupService.resolveEffectiveSpread(999L))
                .isInstanceOf(CurrencyPairNotFoundException.class);
    }
}
