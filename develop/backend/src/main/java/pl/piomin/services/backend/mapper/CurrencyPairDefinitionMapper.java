package pl.piomin.services.backend.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import pl.piomin.services.backend.model.CurrencyPairDefinition;

@Mapper
public interface CurrencyPairDefinitionMapper {

    List<CurrencyPairDefinition> findAll(@Param("baseCurrencyId") Long baseCurrencyId,
                                          @Param("quoteCurrencyId") Long quoteCurrencyId);

    CurrencyPairDefinition findById(@Param("id") Long id);

    // Direction-independent lookup: matches this pair in either direction,
    // mirroring the (pair_key_low, pair_key_high) unique index at the DB level.
    CurrencyPairDefinition findByEitherDirection(@Param("currencyIdA") Long currencyIdA,
                                                  @Param("currencyIdB") Long currencyIdB);

    int insert(CurrencyPairDefinition definition);

    int update(CurrencyPairDefinition definition);

    int deleteById(@Param("id") Long id);

    // Used only by tests to reset the table between runs without requiring
    // the enriched currency joins used by findAll.
    List<Long> findAllIds();
}
