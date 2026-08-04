package com.wdd.backend.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wdd.backend.dto.CurrencyCreateRequest;
import com.wdd.backend.dto.CurrencyResponse;
import com.wdd.backend.dto.CurrencyUpdateRequest;
import com.wdd.backend.exception.CurrencyCodeExistsException;
import com.wdd.backend.exception.CurrencyInUseException;
import com.wdd.backend.exception.CurrencyNotFoundException;
import com.wdd.backend.mapper.CurrencyMapper;
import com.wdd.backend.mapper.CurrencyPairMapper;
import com.wdd.backend.model.Currency;

@Service
public class CurrencyService {

    private final CurrencyMapper currencyMapper;
    private final CurrencyPairMapper currencyPairMapper;

    public CurrencyService(CurrencyMapper currencyMapper, CurrencyPairMapper currencyPairMapper) {
        this.currencyMapper = currencyMapper;
        this.currencyPairMapper = currencyPairMapper;
    }

    public List<CurrencyResponse> list() {
        return currencyMapper.findAll().stream()
                .map(CurrencyResponse::from)
                .collect(Collectors.toList());
    }

    public CurrencyResponse getById(Long id) {
        Currency currency = currencyMapper.findById(id)
                .orElseThrow(() -> new CurrencyNotFoundException(id));
        return CurrencyResponse.from(currency);
    }

    @Transactional
    public CurrencyResponse create(CurrencyCreateRequest request) {
        currencyMapper.findByCode(request.getCode()).ifPresent(existing -> {
            throw new CurrencyCodeExistsException(request.getCode());
        });

        Currency currency = new Currency();
        currency.setCode(request.getCode());
        currency.setName(request.getName());
        currency.setNameZh(request.getNameZh());
        currency.setSymbol(request.getSymbol());
        currency.setDecimalPlaces(request.getDecimalPlaces());

        currencyMapper.insert(currency);

        Currency created = currencyMapper.findById(currency.getId())
                .orElseThrow(() -> new CurrencyNotFoundException(currency.getId()));
        return CurrencyResponse.from(created);
    }

    @Transactional
    public CurrencyResponse update(Long id, CurrencyUpdateRequest request) {
        Currency existing = currencyMapper.findById(id)
                .orElseThrow(() -> new CurrencyNotFoundException(id));

        if (request.getName() != null) {
            existing.setName(request.getName());
        }
        if (request.getNameZh() != null) {
            existing.setNameZh(request.getNameZh());
        }
        if (request.getSymbol() != null) {
            existing.setSymbol(request.getSymbol());
        }
        if (request.getDecimalPlaces() != null) {
            existing.setDecimalPlaces(request.getDecimalPlaces());
        }

        currencyMapper.update(existing);

        Currency updated = currencyMapper.findById(id)
                .orElseThrow(() -> new CurrencyNotFoundException(id));
        return CurrencyResponse.from(updated);
    }

    @Transactional
    public void delete(Long id) {
        currencyMapper.findById(id)
                .orElseThrow(() -> new CurrencyNotFoundException(id));
        if (currencyPairMapper.existsByCurrencyId(id)) {
            throw new CurrencyInUseException(id);
        }
        currencyMapper.deleteById(id);
    }
}
