package com.wdd.backend.service;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

/**
 * {@code list}/{@code getById}/{@code resolveEffectiveSpread} are called directly by
 * {@code SpreadController} — always live, already-approved data, unaffected by the audit
 * workflow. {@code create}/{@code update}/{@code delete} are called only from
 * {@link SpreadGroupAuditHandler#apply} once a CREATE/UPDATE/DELETE audit request has been
 * approved — never directly from {@code SpreadController} (specs/backend/spread.md).
 */
@Service
public class SpreadGroupService {

    private final SpreadGroupMapper spreadGroupMapper;
    private final SpreadGroupMemberMapper spreadGroupMemberMapper;
    private final CurrencyPairMapper currencyPairMapper;
    private final SpreadDefaultMapper spreadDefaultMapper;

    public SpreadGroupService(SpreadGroupMapper spreadGroupMapper, SpreadGroupMemberMapper spreadGroupMemberMapper,
            CurrencyPairMapper currencyPairMapper, SpreadDefaultMapper spreadDefaultMapper) {
        this.spreadGroupMapper = spreadGroupMapper;
        this.spreadGroupMemberMapper = spreadGroupMemberMapper;
        this.currencyPairMapper = currencyPairMapper;
        this.spreadDefaultMapper = spreadDefaultMapper;
    }

    public List<SpreadGroupResponse> list(Long brandId) {
        return spreadGroupMapper.findAll(brandId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public SpreadGroupResponse getById(Long id) {
        SpreadGroup group = spreadGroupMapper.findById(id)
                .orElseThrow(() -> new SpreadGroupNotFoundException(id));
        return toResponse(group);
    }

    /**
     * Inserts the {@code spread_group} row, then for each {@code currencyPairId}: detaches any
     * prior membership for that pair (delete-then-insert), then attaches it to the newly created
     * group.
     */
    @Transactional
    public SpreadGroupResponse create(Long brandId, String name, BigDecimal depositSpread,
            BigDecimal withdrawSpread, List<Long> currencyPairIds) {
        SpreadGroup group = new SpreadGroup();
        group.setBrandId(brandId);
        group.setName(name);
        group.setDepositSpread(depositSpread);
        group.setWithdrawSpread(withdrawSpread);
        spreadGroupMapper.insert(group);

        if (currencyPairIds != null) {
            for (Long currencyPairId : currencyPairIds) {
                spreadGroupMemberMapper.deleteByCurrencyPairId(currencyPairId);
                SpreadGroupMember member = new SpreadGroupMember();
                member.setSpreadGroupId(group.getId());
                member.setCurrencyPairId(currencyPairId);
                spreadGroupMemberMapper.insert(member);
            }
        }

        return getById(group.getId());
    }

    /**
     * Persists {@code name}/{@code depositSpread}/{@code withdrawSpread} onto the existing row.
     * If {@code currencyPairIds} is non-null, replaces the full membership set via a three-way
     * diff: pairs no longer listed are removed (reverting to the default spread), pairs newly
     * listed are attached (detaching them from any other group first), pairs unchanged are left
     * alone. A null {@code currencyPairIds} leaves membership untouched.
     */
    @Transactional
    public SpreadGroupResponse update(Long id, String name, BigDecimal depositSpread, BigDecimal withdrawSpread,
            List<Long> currencyPairIds) {
        SpreadGroup existing = spreadGroupMapper.findById(id)
                .orElseThrow(() -> new SpreadGroupNotFoundException(id));

        existing.setName(name);
        existing.setDepositSpread(depositSpread);
        existing.setWithdrawSpread(withdrawSpread);
        spreadGroupMapper.update(existing);

        if (currencyPairIds != null) {
            Set<Long> desired = new HashSet<>(currencyPairIds);
            Set<Long> current = spreadGroupMemberMapper.findByGroupId(id).stream()
                    .map(SpreadGroupMember::getCurrencyPairId)
                    .collect(Collectors.toSet());

            for (Long currencyPairId : current) {
                if (!desired.contains(currencyPairId)) {
                    spreadGroupMemberMapper.deleteByCurrencyPairId(currencyPairId);
                }
            }

            for (Long currencyPairId : desired) {
                if (!current.contains(currencyPairId)) {
                    // Detach from whichever group (if any) this pair currently belongs to.
                    spreadGroupMemberMapper.deleteByCurrencyPairId(currencyPairId);
                    SpreadGroupMember member = new SpreadGroupMember();
                    member.setSpreadGroupId(id);
                    member.setCurrencyPairId(currencyPairId);
                    spreadGroupMemberMapper.insert(member);
                }
            }
        }

        return getById(id);
    }

    /**
     * Deletes the group's memberships first, then the group row itself — pairs that were members
     * immediately fall back to the default spread.
     */
    @Transactional
    public void delete(Long id) {
        spreadGroupMapper.findById(id).orElseThrow(() -> new SpreadGroupNotFoundException(id));
        spreadGroupMemberMapper.deleteByGroupId(id);
        spreadGroupMapper.deleteById(id);
    }

    /**
     * Always reads live, already-approved {@code spread_group}/{@code spread_group_member}/
     * {@code spread_default} rows — never affected by a PENDING proposal.
     */
    public SpreadResolutionResponse resolveEffectiveSpread(Long currencyPairId) {
        CurrencyPair pair = currencyPairMapper.findById(currencyPairId)
                .orElseThrow(() -> new CurrencyPairNotFoundException(currencyPairId));
        Long brandId = pair.getBrandId();

        Optional<SpreadGroupMember> membership = spreadGroupMemberMapper.findByCurrencyPairId(currencyPairId);
        SpreadResolutionResponse response = new SpreadResolutionResponse();
        response.setCurrencyPairId(currencyPairId);
        response.setBrandId(brandId);

        if (membership.isPresent()) {
            SpreadGroupMember member = membership.get();
            SpreadGroup group = spreadGroupMapper.findById(member.getSpreadGroupId())
                    .orElseThrow(() -> new SpreadGroupNotFoundException(member.getSpreadGroupId()));

            response.setSource("GROUP");
            response.setSpreadGroupId(group.getId());
            response.setSpreadGroupName(group.getName());
            response.setDepositSpread(group.getDepositSpread());
            response.setWithdrawSpread(group.getWithdrawSpread());
            return response;
        }

        SpreadDefault spreadDefault = spreadDefaultMapper.findByBrandId(brandId)
                .orElseThrow(() -> new IllegalStateException("No spread_default row seeded for brand " + brandId));

        response.setSource("DEFAULT");
        response.setSpreadGroupId(null);
        response.setSpreadGroupName(null);
        response.setDepositSpread(spreadDefault.getDepositSpread());
        response.setWithdrawSpread(spreadDefault.getWithdrawSpread());
        return response;
    }

    private SpreadGroupResponse toResponse(SpreadGroup group) {
        List<SpreadGroupMember> members = spreadGroupMemberMapper.findByGroupId(group.getId());
        return SpreadGroupResponse.from(group, members);
    }
}
