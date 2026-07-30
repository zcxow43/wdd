package pl.piomin.services.backend.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import pl.piomin.services.backend.model.SpreadGroup;

@Mapper
public interface SpreadGroupMapper {

    List<SpreadGroup> findAll(@Param("brandId") Long brandId);

    SpreadGroup findById(@Param("id") Long id);

    SpreadGroup findByBrandAndName(@Param("brandId") Long brandId, @Param("name") String name);

    int insert(SpreadGroup spreadGroup);

    int update(SpreadGroup spreadGroup);

    int deleteById(@Param("id") Long id);

    // Used only by tests to reset the table between runs.
    List<Long> findAllIds();
}
