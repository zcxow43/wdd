package com.wdd.backend.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.wdd.backend.audit.AuditActionType;
import com.wdd.backend.audit.AuditHandler;
import com.wdd.backend.dto.CurrencyPairUpdateRequest;
import com.wdd.backend.exception.CurrencyPairNotFoundException;
import com.wdd.backend.mapper.CurrencyPairMapper;
import com.wdd.backend.model.Brand;
import com.wdd.backend.model.Currency;
import com.wdd.backend.model.CurrencyPair;

/**
 * {@link AuditHandler} plug-in for {@code entityType = "CURRENCY_PAIR"}. Handles {@code UPDATE}/
 * {@code DELETE} only — there is no {@code CREATE} case: per a later requirement, a brand's
 * {@code currency_pair} row can only ever come into existence via
 * {@code CurrencyPairDefinitionService}'s per-brand fan-out
 * (specs/backend/currency-pair-definition.md), which calls {@link CurrencyPairService#create}
 * as a plain, unaudited method call. {@code apply(...)}'s switch over {@link AuditActionType}
 * stays exhaustive, with {@code CREATE} throwing {@link UnsupportedOperationException} rather
 * than being silently omitted, so a future accidental re-wiring of a CREATE audit submission for
 * CURRENCY_PAIR fails loudly instead of writing corrupt data.
 */
@Component
public class CurrencyPairAuditHandler implements AuditHandler {

    public static final String ENTITY_TYPE = "CURRENCY_PAIR";

    private final CurrencyPairMapper currencyPairMapper;
    private final CurrencyPairService currencyPairService;
    private final CurrencyPairValidator validator;

    public CurrencyPairAuditHandler(CurrencyPairMapper currencyPairMapper, CurrencyPairService currencyPairService,
            CurrencyPairValidator validator) {
        this.currencyPairMapper = currencyPairMapper;
        this.currencyPairService = currencyPairService;
        this.validator = validator;
    }

    @Override
    public String entityType() {
        return ENTITY_TYPE;
    }

    @Override
    public Map<String, Object> snapshotOf(Long entityId) {
        CurrencyPair pair = currencyPairMapper.findById(entityId)
                .orElseThrow(() -> new CurrencyPairNotFoundException(entityId));
        return toSnapshot(pair);
    }

    /**
     * Only ever invoked with {@code UPDATE} (never {@code CREATE} — there is no such case for
     * this entity type — and never {@code DELETE}, per {@link AuditHandler}'s own contract).
     * Re-validates brand/currency existence, base != quote, the rate/rateType rule, and
     * (brand, base, quote) uniqueness (excluding this row) against the proposed {@code after}
     * snapshot, reusing {@link CurrencyPairValidator} — the same helpers
     * {@link CurrencyPairService} uses. Enriches {@code after} in place with the resolved
     * brand/base/quote codes and the effective (rate-rule-applied) rate.
     */
    @Override
    public void validate(AuditActionType actionType, Long entityId, Map<String, Object> afterSnapshot) {
        Long brandId = toLong(afterSnapshot.get("brandId"));
        Long baseCurrencyId = toLong(afterSnapshot.get("baseCurrencyId"));
        Long quoteCurrencyId = toLong(afterSnapshot.get("quoteCurrencyId"));
        String rateType = (String) afterSnapshot.get("rateType");
        BigDecimal rate = toBigDecimal(afterSnapshot.get("rate"));

        Brand brand = validator.requireBrandExists(brandId);
        Currency baseCurrency = validator.requireCurrencyExists(baseCurrencyId);
        Currency quoteCurrency = validator.requireCurrencyExists(quoteCurrencyId);
        validator.requireDistinct(baseCurrencyId, quoteCurrencyId);
        validator.requireNoConflict(brandId, baseCurrencyId, quoteCurrencyId, entityId);

        BigDecimal effectiveRate = validator.applyRateTypeRule(rateType, rate);

        afterSnapshot.put("brandId", brandId);
        afterSnapshot.put("brandCode", brand.getCode());
        afterSnapshot.put("baseCurrencyId", baseCurrencyId);
        afterSnapshot.put("baseCurrencyCode", baseCurrency.getCode());
        afterSnapshot.put("quoteCurrencyId", quoteCurrencyId);
        afterSnapshot.put("quoteCurrencyCode", quoteCurrency.getCode());
        afterSnapshot.put("rateType", rateType);
        afterSnapshot.put("rate", effectiveRate);
    }

    @Override
    public Long apply(AuditActionType actionType, Long entityId, Map<String, Object> afterSnapshot) {
        switch (actionType) {
            case CREATE:
                throw new UnsupportedOperationException(
                        "CURRENCY_PAIR CREATE is not submitted through the audit workflow — a currency_pair "
                                + "row can only come into existence via CurrencyPairDefinitionService's "
                                + "per-brand fan-out (specs/backend/currency-pair-definition.md)");
            case UPDATE:
                currencyPairService.update(entityId, toUpdateRequest(afterSnapshot));
                return entityId;
            case DELETE:
                currencyPairService.delete(entityId);
                return entityId;
            default:
                throw new IllegalArgumentException("Unknown actionType: " + actionType);
        }
    }

    @Override
    public String summarize(Map<String, Object> snapshot) {
        if (snapshot == null) {
            return ENTITY_TYPE;
        }
        return snapshot.get("brandCode") + " · " + snapshot.get("baseCurrencyCode") + "/"
                + snapshot.get("quoteCurrencyCode");
    }

    private Map<String, Object> toSnapshot(CurrencyPair pair) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("brandId", pair.getBrandId());
        snapshot.put("brandCode", pair.getBrandCode());
        snapshot.put("baseCurrencyId", pair.getBaseCurrencyId());
        snapshot.put("baseCurrencyCode", pair.getBaseCurrencyCode());
        snapshot.put("quoteCurrencyId", pair.getQuoteCurrencyId());
        snapshot.put("quoteCurrencyCode", pair.getQuoteCurrencyCode());
        snapshot.put("rate", pair.getRate());
        snapshot.put("rateType", pair.getRateType());
        snapshot.put("active", pair.getActive());
        return snapshot;
    }

    private CurrencyPairUpdateRequest toUpdateRequest(Map<String, Object> snapshot) {
        CurrencyPairUpdateRequest request = new CurrencyPairUpdateRequest();
        request.setBrandId(toLong(snapshot.get("brandId")));
        request.setBaseCurrencyId(toLong(snapshot.get("baseCurrencyId")));
        request.setQuoteCurrencyId(toLong(snapshot.get("quoteCurrencyId")));
        request.setRate(toBigDecimal(snapshot.get("rate")));
        request.setRateType((String) snapshot.get("rateType"));
        Object active = snapshot.get("active");
        request.setActive(active != null ? (Boolean) active : null);
        return request;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.valueOf(value.toString());
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        return new BigDecimal(value.toString());
    }
}
