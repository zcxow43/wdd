package com.wdd.backend.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.wdd.backend.dto.ExchangeRate;
import com.wdd.backend.dto.ExchangeRateEffectiveSpread;
import com.wdd.backend.dto.ExchangeRateLatest;

@Mapper
public interface ExchangeRateMapper {

    /**
     * One row per {@code (currency_pair_definition, brand)} combination
     * (CROSS JOIN, filtered to one brand when {@code brandId} is given),
     * LEFT JOINed to its most recent {@code exchange_rate} row (by
     * {@code rate_minute}), if any.
     */
    List<ExchangeRateLatest> findLatestPerDefinitionAndBrand(@Param("brandId") Long brandId);

    /**
     * Whole seconds elapsed (floored) since {@code MAX(updated_at)} across
     * the whole table, computed entirely inside MySQL via
     * {@code TIMESTAMPDIFF} against the server's own {@code CURRENT_TIMESTAMP}
     * — deliberately not read into Java and compared against
     * {@code LocalDateTime.now()}, which would silently compare across two
     * different clocks/timezones (the JDBC driver's {@code serverTimezone}
     * conversion of the TIMESTAMP column vs. the JVM's local zone) and
     * produce a bogus elapsed duration. {@code null} if the table is empty.
     */
    Long findSecondsSinceLastUpdate();

    /**
     * Every {@code (currency_pair_definition, brand)} combination's
     * currently-effective deposit/withdrawal spread, resolved in SQL (group's
     * if the brand's {@code currency_pair} row for that definition is
     * assigned to a spread group, otherwise the brand's {@code brand_spread}
     * default). Used by the sync to compute each brand's snapshot
     * {@code depositRate}/{@code withdrawalRate} at sync time.
     */
    List<ExchangeRateEffectiveSpread> findEffectiveSpreadsForAllBrands();

    /**
     * Inserts a new snapshot, or refreshes the existing one for the same
     * {@code (currency_pair_definition_id, brand_id, rate_minute)} if a
     * second sync lands in the same minute.
     */
    int upsert(ExchangeRate exchangeRate);

}
