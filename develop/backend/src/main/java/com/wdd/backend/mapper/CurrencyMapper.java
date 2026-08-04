package com.wdd.backend.mapper;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;

import com.wdd.backend.model.Currency;

@Mapper
public interface CurrencyMapper {

    List<Currency> findAll();

    Optional<Currency> findById(Long id);

    Optional<Currency> findByCode(String code);

    int insert(Currency currency);

    int update(Currency currency);

    int deleteById(Long id);
}
