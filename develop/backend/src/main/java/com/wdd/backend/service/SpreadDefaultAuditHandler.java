package com.wdd.backend.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.wdd.backend.audit.AuditActionType;
import com.wdd.backend.audit.AuditHandler;
import com.wdd.backend.dto.SpreadDefaultResponse;

/**
 * {@link AuditHandler} plug-in for {@code entityType = "SPREAD_DEFAULT"} (specs/backend/spread.md).
 * Handles {@code UPDATE} only — a {@code spread_default} row is seeded 1:1 per brand
 * (specs/dba/spread-default.md) and never created/deleted through the API. {@code apply(...)}'s
 * switch over {@link AuditActionType} stays exhaustive, with {@code CREATE}/{@code DELETE}
 * throwing {@link UnsupportedOperationException} rather than being silently omitted.
 */
@Component
public class SpreadDefaultAuditHandler implements AuditHandler {

    public static final String ENTITY_TYPE = "SPREAD_DEFAULT";

    private final SpreadDefaultService spreadDefaultService;
    private final SpreadGroupValidator validator;

    public SpreadDefaultAuditHandler(SpreadDefaultService spreadDefaultService, SpreadGroupValidator validator) {
        this.spreadDefaultService = spreadDefaultService;
        this.validator = validator;
    }

    @Override
    public String entityType() {
        return ENTITY_TYPE;
    }

    @Override
    public Map<String, Object> snapshotOf(Long entityId) {
        SpreadDefaultResponse current = spreadDefaultService.getById(entityId);
        return toSnapshot(current);
    }

    /**
     * Only ever invoked with {@code UPDATE}. Re-validates depositSpread/withdrawSpread {@code >=
     * 0} via the shared {@link SpreadGroupValidator}, and enriches {@code after} in place with
     * the row's (immutable) {@code brandId}/{@code brandCode} — mirroring
     * {@code CurrencyPairAuditHandler}'s enrichment pattern — so the persisted snapshot matches
     * the shape documented in the spec.
     */
    @Override
    public void validate(AuditActionType actionType, Long entityId, Map<String, Object> afterSnapshot) {
        BigDecimal depositSpread = toBigDecimal(afterSnapshot.get("depositSpread"));
        BigDecimal withdrawSpread = toBigDecimal(afterSnapshot.get("withdrawSpread"));
        validator.requireSpreadNonNegative(depositSpread, withdrawSpread);

        SpreadDefaultResponse current = spreadDefaultService.getById(entityId);

        afterSnapshot.put("brandId", current.getBrandId());
        afterSnapshot.put("brandCode", current.getBrandCode());
        afterSnapshot.put("depositSpread", depositSpread);
        afterSnapshot.put("withdrawSpread", withdrawSpread);
    }

    @Override
    public Long apply(AuditActionType actionType, Long entityId, Map<String, Object> afterSnapshot) {
        switch (actionType) {
            case CREATE:
                throw new UnsupportedOperationException(
                        "SPREAD_DEFAULT CREATE is not submitted through the audit workflow — a spread_default "
                                + "row is seeded 1:1 per brand and never created through the API");
            case UPDATE:
                BigDecimal depositSpread = toBigDecimal(afterSnapshot.get("depositSpread"));
                BigDecimal withdrawSpread = toBigDecimal(afterSnapshot.get("withdrawSpread"));
                spreadDefaultService.update(entityId, depositSpread, withdrawSpread);
                return entityId;
            case DELETE:
                throw new UnsupportedOperationException(
                        "SPREAD_DEFAULT DELETE is not submitted through the audit workflow — a spread_default "
                                + "row is never removed through the API");
            default:
                throw new IllegalArgumentException("Unknown actionType: " + actionType);
        }
    }

    @Override
    public String summarize(Map<String, Object> snapshot) {
        if (snapshot == null) {
            return ENTITY_TYPE;
        }
        return snapshot.get("brandCode") + " · 預設點差";
    }

    private Map<String, Object> toSnapshot(SpreadDefaultResponse spreadDefault) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("brandId", spreadDefault.getBrandId());
        snapshot.put("brandCode", spreadDefault.getBrandCode());
        snapshot.put("depositSpread", spreadDefault.getDepositSpread());
        snapshot.put("withdrawSpread", spreadDefault.getWithdrawSpread());
        return snapshot;
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
