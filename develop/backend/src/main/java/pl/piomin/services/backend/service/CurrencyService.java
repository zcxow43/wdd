package pl.piomin.services.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pl.piomin.services.backend.dto.CurrencyCreateRequest;
import pl.piomin.services.backend.dto.CurrencyUpdateRequest;
import pl.piomin.services.backend.exception.CurrencyCodeExistsException;
import pl.piomin.services.backend.exception.CurrencyInUseException;
import pl.piomin.services.backend.exception.CurrencyNotFoundException;
import pl.piomin.services.backend.mapper.CurrencyMapper;
import pl.piomin.services.backend.mapper.CurrencyPairMapper;
import pl.piomin.services.backend.model.Currency;

@Service
public class CurrencyService {

    private final CurrencyMapper currencyMapper;
    private final CurrencyPairMapper currencyPairMapper;

    public CurrencyService(CurrencyMapper currencyMapper, CurrencyPairMapper currencyPairMapper) {
        this.currencyMapper = currencyMapper;
        this.currencyPairMapper = currencyPairMapper;
    }

    public List<Currency> list(Boolean active) {
        return currencyMapper.findAll(active);
    }

    public Currency getById(Long id) {
        Currency currency = currencyMapper.findById(id);
        if (currency == null) {
            throw new CurrencyNotFoundException(id);
        }
        return currency;
    }

    @Transactional
    public Currency create(CurrencyCreateRequest request) {
        if (currencyMapper.findByCode(request.getCode()) != null) {
            throw new CurrencyCodeExistsException(request.getCode());
        }

        Currency currency = new Currency();
        currency.setCode(request.getCode());
        currency.setName(request.getName());
        currency.setNameZh(request.getNameZh());
        currency.setSymbol(request.getSymbol());
        currency.setDecimalPlaces(request.getDecimalPlaces());
        currency.setActive(request.getActive() != null ? request.getActive() : Boolean.TRUE);

        currencyMapper.insert(currency);
        return currencyMapper.findById(currency.getId());
    }

    @Transactional
    public Currency update(Long id, CurrencyUpdateRequest request) {
        Currency existing = currencyMapper.findById(id);
        if (existing == null) {
            throw new CurrencyNotFoundException(id);
        }

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
        if (request.getActive() != null) {
            existing.setActive(request.getActive());
        }

        currencyMapper.update(existing);
        return currencyMapper.findById(id);
    }

    @Transactional
    public void delete(Long id) {
        Currency existing = currencyMapper.findById(id);
        if (existing == null) {
            throw new CurrencyNotFoundException(id);
        }
        if (currencyPairMapper.existsByCurrencyId(id)) {
            throw new CurrencyInUseException(id);
        }
        currencyMapper.deleteById(id);
    }
}
