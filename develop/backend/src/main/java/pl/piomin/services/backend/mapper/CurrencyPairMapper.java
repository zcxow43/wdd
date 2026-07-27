package pl.piomin.services.backend.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import pl.piomin.services.backend.model.CurrencyPair;

@Mapper
public interface CurrencyPairMapper {

    List<CurrencyPair> findAll(@Param("brandId") Long brandId, @Param("active") Boolean active);

    CurrencyPair findById(@Param("id") Long id);

    CurrencyPair findByBrandBaseQuote(@Param("brandId") Long brandId,
                                       @Param("baseCurrencyId") Long baseCurrencyId,
                                       @Param("quoteCurrencyId") Long quoteCurrencyId);

    int insert(CurrencyPair currencyPair);

    int update(CurrencyPair currencyPair);

    int deleteById(@Param("id") Long id);

    boolean existsByCurrencyId(@Param("currencyId") Long currencyId);

    // Used only by tests to reset the currency_pair table between runs without
    // requiring the enriched brand/currency joins used by findAll.
    List<Long> findAllIds();
}
