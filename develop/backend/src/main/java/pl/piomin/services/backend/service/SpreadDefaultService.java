package pl.piomin.services.backend.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pl.piomin.services.backend.exception.SpreadDefaultNotFoundException;
import pl.piomin.services.backend.mapper.SpreadDefaultMapper;
import pl.piomin.services.backend.model.SpreadDefault;

/**
 * Reads {@code spread_default} and applies updates. One row exists per brand
 * from the moment that brand is seeded (specs/dba/spread.md) - never
 * created/deleted through the API. As of the audit-approval delta
 * (specs/backend/spread.md), {@link #update} is no longer called directly by
 * {@code SpreadController} - it is only invoked by
 * {@code SpreadDefaultAuditHandler.apply(...)} once a change request has been approved.
 */
@Service
public class SpreadDefaultService {

    private final SpreadDefaultMapper spreadDefaultMapper;

    public SpreadDefaultService(SpreadDefaultMapper spreadDefaultMapper) {
        this.spreadDefaultMapper = spreadDefaultMapper;
    }

    public List<SpreadDefault> list(Long brandId) {
        return spreadDefaultMapper.findAll(brandId);
    }

    public SpreadDefault getById(Long id) {
        SpreadDefault spreadDefault = spreadDefaultMapper.findById(id);
        if (spreadDefault == null) {
            throw new SpreadDefaultNotFoundException(id);
        }
        return spreadDefault;
    }

    @Transactional
    public SpreadDefault update(Long id, BigDecimal depositSpread, BigDecimal withdrawSpread) {
        SpreadDefault existing = spreadDefaultMapper.findById(id);
        if (existing == null) {
            throw new SpreadDefaultNotFoundException(id);
        }
        existing.setDepositSpread(depositSpread);
        existing.setWithdrawSpread(withdrawSpread);
        spreadDefaultMapper.update(existing);
        return spreadDefaultMapper.findById(id);
    }
}
