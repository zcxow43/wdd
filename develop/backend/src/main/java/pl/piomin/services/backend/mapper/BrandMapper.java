package pl.piomin.services.backend.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import pl.piomin.services.backend.model.Brand;

@Mapper
public interface BrandMapper {

    List<Brand> findAll(@Param("active") Boolean active);

    Brand findById(@Param("id") Long id);

    int update(Brand brand);

    // Used only by tests to seed/clean the fixed brand set (H2 in-memory schema);
    // brands are seeded exclusively via migration in production, never via the API.
    int insert(Brand brand);

    int deleteById(@Param("id") Long id);
}
