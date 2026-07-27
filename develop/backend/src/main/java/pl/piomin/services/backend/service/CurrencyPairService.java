package pl.piomin.services.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pl.piomin.services.backend.dto.CurrencyPairCreateRequest;
import pl.piomin.services.backend.dto.CurrencyPairUpdateRequest;
import pl.piomin.services.backend.exception.BrandNotFoundException;
import pl.piomin.services.backend.exception.CurrencyNotFoundException;
import pl.piomin.services.backend.exception.CurrencyPairExistsException;
import pl.piomin.services.backend.exception.CurrencyPairNotFoundException;
import pl.piomin.services.backend.exception.InvalidCurrencyPairException;
import pl.piomin.services.backend.mapper.BrandMapper;
import pl.piomin.services.backend.mapper.CurrencyMapper;
import pl.piomin.services.backend.mapper.CurrencyPairMapper;
import pl.piomin.services.backend.model.CurrencyPair;

@Service
public class CurrencyPairService {

    private final CurrencyPairMapper currencyPairMapper;
    private final BrandMapper brandMapper;
    private final CurrencyMapper currencyMapper;

    public CurrencyPairService(CurrencyPairMapper currencyPairMapper, BrandMapper brandMapper,
                                CurrencyMapper currencyMapper) {
        this.currencyPairMapper = currencyPairMapper;
        this.brandMapper = brandMapper;
        this.currencyMapper = currencyMapper;
    }

    public List<CurrencyPair> list(Long brandId, Boolean active) {
        return currencyPairMapper.findAll(brandId, active);
    }

    public CurrencyPair getById(Long id) {
        CurrencyPair pair = currencyPairMapper.findById(id);
        if (pair == null) {
            throw new CurrencyPairNotFoundException(id);
        }
        return pair;
    }

    @Transactional
    public CurrencyPair create(CurrencyPairCreateRequest request) {
        validateBrandExists(request.getBrandId());
        validateCurrencyExists(request.getBaseCurrencyId());
        validateCurrencyExists(request.getQuoteCurrencyId());
        validateDistinct(request.getBaseCurrencyId(), request.getQuoteCurrencyId());
        validateUnique(request.getBrandId(), request.getBaseCurrencyId(), request.getQuoteCurrencyId(), null);

        CurrencyPair pair = new CurrencyPair();
        pair.setBrandId(request.getBrandId());
        pair.setBaseCurrencyId(request.getBaseCurrencyId());
        pair.setQuoteCurrencyId(request.getQuoteCurrencyId());
        pair.setRate(request.getRate());
        pair.setRateType(request.getRateType());
        pair.setActive(request.getActive() != null ? request.getActive() : Boolean.TRUE);

        currencyPairMapper.insert(pair);
        return currencyPairMapper.findById(pair.getId());
    }

    @Transactional
    public CurrencyPair update(Long id, CurrencyPairUpdateRequest request) {
        CurrencyPair existing = currencyPairMapper.findById(id);
        if (existing == null) {
            throw new CurrencyPairNotFoundException(id);
        }

        Long brandId = request.getBrandId() != null ? request.getBrandId() : existing.getBrandId();
        Long baseCurrencyId = request.getBaseCurrencyId() != null
                ? request.getBaseCurrencyId() : existing.getBaseCurrencyId();
        Long quoteCurrencyId = request.getQuoteCurrencyId() != null
                ? request.getQuoteCurrencyId() : existing.getQuoteCurrencyId();

        if (request.getBrandId() != null) {
            validateBrandExists(brandId);
        }
        if (request.getBaseCurrencyId() != null) {
            validateCurrencyExists(baseCurrencyId);
        }
        if (request.getQuoteCurrencyId() != null) {
            validateCurrencyExists(quoteCurrencyId);
        }
        validateDistinct(baseCurrencyId, quoteCurrencyId);
        validateUnique(brandId, baseCurrencyId, quoteCurrencyId, id);

        existing.setBrandId(brandId);
        existing.setBaseCurrencyId(baseCurrencyId);
        existing.setQuoteCurrencyId(quoteCurrencyId);
        if (request.getRate() != null) {
            existing.setRate(request.getRate());
        }
        if (request.getRateType() != null) {
            existing.setRateType(request.getRateType());
        }
        if (request.getActive() != null) {
            existing.setActive(request.getActive());
        }

        currencyPairMapper.update(existing);
        return currencyPairMapper.findById(id);
    }

    @Transactional
    public void delete(Long id) {
        CurrencyPair existing = currencyPairMapper.findById(id);
        if (existing == null) {
            throw new CurrencyPairNotFoundException(id);
        }
        currencyPairMapper.deleteById(id);
    }

    private void validateBrandExists(Long brandId) {
        if (brandMapper.findById(brandId) == null) {
            throw new BrandNotFoundException(brandId);
        }
    }

    private void validateCurrencyExists(Long currencyId) {
        if (currencyMapper.findById(currencyId) == null) {
            throw new CurrencyNotFoundException(currencyId);
        }
    }

    private void validateDistinct(Long baseCurrencyId, Long quoteCurrencyId) {
        if (baseCurrencyId != null && baseCurrencyId.equals(quoteCurrencyId)) {
            throw new InvalidCurrencyPairException("Base and quote currency must differ");
        }
    }

    private void validateUnique(Long brandId, Long baseCurrencyId, Long quoteCurrencyId, Long excludeId) {
        CurrencyPair other = currencyPairMapper.findByBrandBaseQuote(brandId, baseCurrencyId, quoteCurrencyId);
        if (other != null && !other.getId().equals(excludeId)) {
            throw new CurrencyPairExistsException(brandId, baseCurrencyId, quoteCurrencyId);
        }
    }
}
