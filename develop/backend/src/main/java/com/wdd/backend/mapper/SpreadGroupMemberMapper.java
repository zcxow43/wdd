package com.wdd.backend.mapper;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;

import com.wdd.backend.model.SpreadGroupMember;

@Mapper
public interface SpreadGroupMemberMapper {

    List<SpreadGroupMember> findByGroupId(Long spreadGroupId);

    /**
     * A currency pair belongs to at most one group at a time (UNIQUE on {@code currency_pair_id}
     * at the DB level) — used both by the resolver endpoint and by the membership-diff logic in
     * {@code SpreadGroupService.create}/{@code update}.
     */
    Optional<SpreadGroupMember> findByCurrencyPairId(Long currencyPairId);

    int insert(SpreadGroupMember member);

    int deleteByCurrencyPairId(Long currencyPairId);

    int deleteByGroupId(Long spreadGroupId);
}
