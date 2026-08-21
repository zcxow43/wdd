package com.wdd.backend.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.wdd.backend.dto.Currency;

@Mapper
public interface CurrencyMapper {

    List<Currency> findAll();

    Currency findById(@Param("id") Long id);

    Currency findByCode(@Param("code") String code);

    int insert(Currency currency);

    int update(@Param("id") Long id, @Param("name") String name, @Param("symbol") String symbol,
            @Param("decimalPlaces") Integer decimalPlaces);

    int deleteById(@Param("id") Long id);

}
