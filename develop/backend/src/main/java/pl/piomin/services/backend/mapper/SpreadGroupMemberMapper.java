package pl.piomin.services.backend.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import pl.piomin.services.backend.model.SpreadGroupMember;

@Mapper
public interface SpreadGroupMemberMapper {

    List<SpreadGroupMember> findByGroupId(@Param("groupId") Long groupId);

    SpreadGroupMember findByCurrencyPairId(@Param("currencyPairId") Long currencyPairId);

    int insert(SpreadGroupMember member);

    int deleteByCurrencyPairId(@Param("currencyPairId") Long currencyPairId);

    int deleteByGroupId(@Param("groupId") Long groupId);

    int deleteById(@Param("id") Long id);

    // Used only by tests to reset the table between runs.
    List<Long> findAllIds();
}
