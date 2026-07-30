package pl.piomin.services.backend.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import pl.piomin.services.backend.model.SpreadDefault;

@Mapper
public interface SpreadDefaultMapper {

    List<SpreadDefault> findAll(@Param("brandId") Long brandId);

    SpreadDefault findById(@Param("id") Long id);

    SpreadDefault findByBrandId(@Param("brandId") Long brandId);

    int update(SpreadDefault spreadDefault);

    // Used only by tests to seed/clean the table (H2 in-memory schema); in
    // production spread_default rows are seeded exclusively via migration
    // (one per brand), never via the API.
    int insert(SpreadDefault spreadDefault);

    int deleteById(@Param("id") Long id);

    List<Long> findAllIds();
}
