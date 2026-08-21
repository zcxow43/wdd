package com.wdd.backend.service;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.wdd.backend.dto.Currency;
import com.wdd.backend.dto.CurrencyCreateRequest;
import com.wdd.backend.dto.CurrencyResponse;
import com.wdd.backend.dto.CurrencyUpdateRequest;
import com.wdd.backend.exception.CurrencyCodeConflictException;
import com.wdd.backend.exception.CurrencyNotFoundException;
import com.wdd.backend.exception.InvalidRequestException;
import com.wdd.backend.mapper.CurrencyMapper;

@Service
public class CurrencyService {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z]{3}$");

    private final CurrencyMapper currencyMapper;

    public CurrencyService(CurrencyMapper currencyMapper) {
        this.currencyMapper = currencyMapper;
    }

    public List<CurrencyResponse> findAll() {
        return currencyMapper.findAll().stream()
                .map(CurrencyService::toResponse)
                .toList();
    }

    public CurrencyResponse findById(Long id) {
        Currency currency = currencyMapper.findById(id);
        if (currency == null) {
            throw new CurrencyNotFoundException(id);
        }
        return toResponse(currency);
    }

    @Transactional
    public CurrencyResponse create(CurrencyCreateRequest request) {
        if (request == null) {
            throw new InvalidRequestException("request body is required");
        }

        String code = request.getCode();
        if (!StringUtils.hasText(code) || !CODE_PATTERN.matcher(code).matches()) {
            throw new InvalidRequestException("code must be exactly 3 uppercase letters");
        }

        validateNameSymbolDecimalPlaces(request.getName(), request.getSymbol(), request.getDecimalPlaces());

        if (currencyMapper.findByCode(code) != null) {
            throw new CurrencyCodeConflictException(code);
        }

        Currency currency = new Currency();
        currency.setCode(code);
        currency.setName(request.getName());
        currency.setSymbol(request.getSymbol());
        currency.setDecimalPlaces(request.getDecimalPlaces());
        currencyMapper.insert(currency);

        Currency created = currencyMapper.findById(currency.getId());
        return toResponse(created);
    }

    @Transactional
    public CurrencyResponse update(Long id, CurrencyUpdateRequest request) {
        if (request == null) {
            throw new InvalidRequestException("request body is required");
        }

        Currency existing = currencyMapper.findById(id);
        if (existing == null) {
            throw new CurrencyNotFoundException(id);
        }

        validateNameSymbolDecimalPlaces(request.getName(), request.getSymbol(), request.getDecimalPlaces());

        currencyMapper.update(id, request.getName(), request.getSymbol(), request.getDecimalPlaces());

        Currency updated = currencyMapper.findById(id);
        return toResponse(updated);
    }

    @Transactional
    public void delete(Long id) {
        Currency existing = currencyMapper.findById(id);
        if (existing == null) {
            throw new CurrencyNotFoundException(id);
        }
        currencyMapper.deleteById(id);
    }

    private static void validateNameSymbolDecimalPlaces(String name, String symbol, Integer decimalPlaces) {
        if (!StringUtils.hasText(name)) {
            throw new InvalidRequestException("name is required");
        }
        if (!StringUtils.hasText(symbol)) {
            throw new InvalidRequestException("symbol is required");
        }
        if (decimalPlaces == null || decimalPlaces < 0 || decimalPlaces > 8) {
            throw new InvalidRequestException("decimalPlaces must be between 0 and 8");
        }
    }

    private static CurrencyResponse toResponse(Currency currency) {
        return new CurrencyResponse(
                currency.getId(),
                currency.getCode(),
                currency.getName(),
                currency.getSymbol(),
                currency.getDecimalPlaces(),
                currency.getCreatedAt(),
                currency.getUpdatedAt());
    }
}
