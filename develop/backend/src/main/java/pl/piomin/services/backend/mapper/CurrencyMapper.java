package pl.piomin.services.backend.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import pl.piomin.services.backend.model.Currency;

@Mapper
public interface CurrencyMapper {

    List<Currency> findAll(@Param("active") Boolean active);

    Currency findById(@Param("id") Long id);

    Currency findByCode(@Param("code") String code);

    int insert(Currency currency);

    int update(Currency currency);

    int deleteById(@Param("id") Long id);
}
