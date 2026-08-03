package pl.piomin.services.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pl.piomin.services.backend.exception.CurrencyCodeExistsException;
import pl.piomin.services.backend.exception.CurrencyNotFoundException;
import pl.piomin.services.backend.mapper.CurrencyMapper;
import pl.piomin.services.backend.model.Currency;

@Service
public class CurrencyService {

    private final CurrencyMapper currencyMapper;

    public CurrencyService(CurrencyMapper currencyMapper) {
        this.currencyMapper = currencyMapper;
    }

    public List<Currency> list() {
        return currencyMapper.findAll();
    }

    public Currency getById(Long id) {
        return currencyMapper.findById(id)
                .orElseThrow(() -> new CurrencyNotFoundException(id));
    }

    @Transactional
    public Currency create(Currency currency) {
        currencyMapper.findByCode(currency.getCode())
                .ifPresent(existing -> {
                    throw new CurrencyCodeExistsException(currency.getCode());
                });
        currencyMapper.insert(currency);
        return getById(currency.getId());
    }

    @Transactional
    public Currency update(Long id, Currency patch) {
        Currency existing = getById(id);

        if (patch.getCode() != null) {
            currencyMapper.findByCode(patch.getCode())
                    .filter(other -> !other.getId().equals(id))
                    .ifPresent(other -> {
                        throw new CurrencyCodeExistsException(patch.getCode());
                    });
            existing.setCode(patch.getCode());
        }
        if (patch.getName() != null) {
            existing.setName(patch.getName());
        }
        if (patch.getNameZh() != null) {
            existing.setNameZh(patch.getNameZh());
        }
        if (patch.getSymbol() != null) {
            existing.setSymbol(patch.getSymbol());
        }
        if (patch.getDecimalPlaces() != null) {
            existing.setDecimalPlaces(patch.getDecimalPlaces());
        }

        currencyMapper.update(existing);
        return getById(id);
    }

    @Transactional
    public void delete(Long id) {
        getById(id);
        currencyMapper.deleteById(id);
    }
}
