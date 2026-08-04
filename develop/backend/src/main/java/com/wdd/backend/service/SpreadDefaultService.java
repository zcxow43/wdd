package com.wdd.backend.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wdd.backend.dto.SpreadDefaultResponse;
import com.wdd.backend.exception.SpreadDefaultNotFoundException;
import com.wdd.backend.mapper.SpreadDefaultMapper;
import com.wdd.backend.model.SpreadDefault;

/**
 * {@code list}/{@code getById} are called directly by {@code SpreadController} for the GET
 * endpoints (unaffected by the audit workflow — always live, already-approved data).
 * {@code update} is called only from {@link SpreadDefaultAuditHandler#apply} once an UPDATE audit
 * request has been approved — never directly from {@code SpreadController}
 * (specs/backend/spread.md). There is no {@code create}/{@code delete} — a {@code spread_default}
 * row is seeded 1:1 per brand and never created/removed through the API.
 */
@Service
public class SpreadDefaultService {

    private final SpreadDefaultMapper spreadDefaultMapper;

    public SpreadDefaultService(SpreadDefaultMapper spreadDefaultMapper) {
        this.spreadDefaultMapper = spreadDefaultMapper;
    }

    public List<SpreadDefaultResponse> list(Long brandId) {
        return spreadDefaultMapper.findAll(brandId).stream()
                .map(SpreadDefaultResponse::from)
                .collect(Collectors.toList());
    }

    public SpreadDefaultResponse getById(Long id) {
        SpreadDefault spreadDefault = spreadDefaultMapper.findById(id)
                .orElseThrow(() -> new SpreadDefaultNotFoundException(id));
        return SpreadDefaultResponse.from(spreadDefault);
    }

    @Transactional
    public SpreadDefaultResponse update(Long id, BigDecimal depositSpread, BigDecimal withdrawSpread) {
        SpreadDefault existing = spreadDefaultMapper.findById(id)
                .orElseThrow(() -> new SpreadDefaultNotFoundException(id));

        existing.setDepositSpread(depositSpread);
        existing.setWithdrawSpread(withdrawSpread);
        spreadDefaultMapper.update(existing);

        SpreadDefault updated = spreadDefaultMapper.findById(id)
                .orElseThrow(() -> new SpreadDefaultNotFoundException(id));
        return SpreadDefaultResponse.from(updated);
    }
}
