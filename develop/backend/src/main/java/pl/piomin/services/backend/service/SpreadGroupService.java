package pl.piomin.services.backend.service;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

/**
 * Reads {@code spread_group}/{@code spread_group_member} (used directly by
 * {@code SpreadController} for GET and the effective-spread resolver, both
 * unaffected by the audit workflow) and applies create/update/delete. As of
 * the audit-approval delta (specs/backend/spread.md), the mutating methods
 * are no longer called directly by {@code SpreadController} - they are only
 * invoked by {@code SpreadGroupAuditHandler.apply(...)} once a change request
 * has been approved.
 */
@Service
public class SpreadGroupService {

    private final SpreadGroupMapper spreadGroupMapper;
    private final SpreadGroupMemberMapper spreadGroupMemberMapper;
    private final SpreadDefaultMapper spreadDefaultMapper;
    private final CurrencyPairMapper currencyPairMapper;

    public SpreadGroupService(SpreadGroupMapper spreadGroupMapper, SpreadGroupMemberMapper spreadGroupMemberMapper,
                               SpreadDefaultMapper spreadDefaultMapper, CurrencyPairMapper currencyPairMapper) {
        this.spreadGroupMapper = spreadGroupMapper;
        this.spreadGroupMemberMapper = spreadGroupMemberMapper;
        this.spreadDefaultMapper = spreadDefaultMapper;
        this.currencyPairMapper = currencyPairMapper;
    }

    public List<SpreadGroup> list(Long brandId) {
        return spreadGroupMapper.findAll(brandId);
    }

    public SpreadGroup getById(Long id) {
        SpreadGroup group = spreadGroupMapper.findById(id);
        if (group == null) {
            throw new SpreadGroupNotFoundException(id);
        }
        return group;
    }

    public List<SpreadGroupMember> getMembers(Long groupId) {
        return spreadGroupMemberMapper.findByGroupId(groupId);
    }

    @Transactional
    public SpreadGroup create(Long brandId, String name, BigDecimal depositSpread, BigDecimal withdrawSpread,
                               List<Long> currencyPairIds) {
        SpreadGroup group = new SpreadGroup();
        group.setBrandId(brandId);
        group.setName(name);
        group.setDepositSpread(depositSpread);
        group.setWithdrawSpread(withdrawSpread);
        spreadGroupMapper.insert(group);

        if (currencyPairIds != null) {
            for (Long pairId : currencyPairIds) {
                // Detach from any prior group (a pair belongs to at most one group).
                spreadGroupMemberMapper.deleteByCurrencyPairId(pairId);
                SpreadGroupMember member = new SpreadGroupMember();
                member.setSpreadGroupId(group.getId());
                member.setCurrencyPairId(pairId);
                spreadGroupMemberMapper.insert(member);
            }
        }
        return spreadGroupMapper.findById(group.getId());
    }

    @Transactional
    public SpreadGroup update(Long id, String name, BigDecimal depositSpread, BigDecimal withdrawSpread,
                               List<Long> currencyPairIds) {
        SpreadGroup existing = spreadGroupMapper.findById(id);
        if (existing == null) {
            throw new SpreadGroupNotFoundException(id);
        }

        existing.setName(name);
        existing.setDepositSpread(depositSpread);
        existing.setWithdrawSpread(withdrawSpread);
        spreadGroupMapper.update(existing);

        if (currencyPairIds != null) {
            List<Long> current = spreadGroupMemberMapper.findByGroupId(id).stream()
                    .map(SpreadGroupMember::getCurrencyPairId).toList();
            Set<Long> desired = new HashSet<>(currencyPairIds);
            Set<Long> currentSet = new HashSet<>(current);

            // Remove pairs no longer in the desired set - they revert to the default spread.
            for (Long pairId : current) {
                if (!desired.contains(pairId)) {
                    spreadGroupMemberMapper.deleteByCurrencyPairId(pairId);
                }
            }
            // Add newly-desired pairs, detaching each from any other group first.
            for (Long pairId : currencyPairIds) {
                if (!currentSet.contains(pairId)) {
                    spreadGroupMemberMapper.deleteByCurrencyPairId(pairId);
                    SpreadGroupMember member = new SpreadGroupMember();
                    member.setSpreadGroupId(id);
                    member.setCurrencyPairId(pairId);
                    spreadGroupMemberMapper.insert(member);
                }
            }
        }
        return spreadGroupMapper.findById(id);
    }

    @Transactional
    public void delete(Long id) {
        SpreadGroup existing = spreadGroupMapper.findById(id);
        if (existing == null) {
            throw new SpreadGroupNotFoundException(id);
        }
        spreadGroupMemberMapper.deleteByGroupId(id);
        spreadGroupMapper.deleteById(id);
    }

    /**
     * Always reads live, already-approved data - unaffected by any PENDING
     * audit request.
     */
    public SpreadResolutionResponse resolveEffectiveSpread(Long currencyPairId) {
        CurrencyPair pair = currencyPairMapper.findById(currencyPairId);
        if (pair == null) {
            throw new CurrencyPairNotFoundException(currencyPairId);
        }

        SpreadGroupMember member = spreadGroupMemberMapper.findByCurrencyPairId(currencyPairId);
        if (member != null) {
            SpreadGroup group = spreadGroupMapper.findById(member.getSpreadGroupId());
            return new SpreadResolutionResponse(currencyPairId, pair.getBrandId(), "GROUP", group.getId(),
                    group.getName(), group.getDepositSpread(), group.getWithdrawSpread());
        }

        SpreadDefault spreadDefault = spreadDefaultMapper.findByBrandId(pair.getBrandId());
        BigDecimal depositSpread = spreadDefault != null ? spreadDefault.getDepositSpread() : BigDecimal.ZERO;
        BigDecimal withdrawSpread = spreadDefault != null ? spreadDefault.getWithdrawSpread() : BigDecimal.ZERO;
        return new SpreadResolutionResponse(currencyPairId, pair.getBrandId(), "DEFAULT", null, null,
                depositSpread, withdrawSpread);
    }
}
