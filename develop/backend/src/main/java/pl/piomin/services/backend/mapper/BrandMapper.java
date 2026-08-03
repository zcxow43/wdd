package pl.piomin.services.backend.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import pl.piomin.services.backend.model.Brand;

@Mapper
public interface BrandMapper {

    List<Brand> findAll(@Param("active") Boolean active);

    Brand findById(@Param("id") Long id);

    int updateActive(@Param("id") Long id, @Param("active") Boolean active);
}
