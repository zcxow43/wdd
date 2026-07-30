package pl.piomin.services.backend.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import pl.piomin.services.backend.audit.AuditActionType;
import pl.piomin.services.backend.audit.AuditHandler;
import pl.piomin.services.backend.model.SpreadDefault;

/**
 * Plugs {@code spread_default} updates into the generic audit module
 * (specs/backend/audit.md) as {@code entityType = "SPREAD_DEFAULT"}. Only
 * ever invoked with UPDATE - a {@code spread_default} row is never
 * created/deleted through the API. Reuses {@link SpreadGroupValidator}'s
 * shared non-negative spread check and {@link SpreadDefaultService} to
 * actually apply an approved change.
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
        return toSnapshot(spreadDefaultService.getById(entityId));
    }

    @Override
    public void validate(AuditActionType actionType, Long entityId, Map<String, Object> afterSnapshot) {
        BigDecimal depositSpread = asBigDecimal(afterSnapshot.get("depositSpread"));
        BigDecimal withdrawSpread = asBigDecimal(afterSnapshot.get("withdrawSpread"));
        validator.validateSpreadNonNegative(depositSpread, withdrawSpread);
    }

    @Override
    public Long apply(AuditActionType actionType, Long entityId, Map<String, Object> afterSnapshot) {
        spreadDefaultService.update(entityId, asBigDecimal(afterSnapshot.get("depositSpread")),
                asBigDecimal(afterSnapshot.get("withdrawSpread")));
        return entityId;
    }

    @Override
    public String summarize(Map<String, Object> snapshot) {
        return snapshot.get("brandCode") + " · 預設點差";
    }

    private Map<String, Object> toSnapshot(SpreadDefault spreadDefault) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("brandId", spreadDefault.getBrandId());
        snapshot.put("brandCode", spreadDefault.getBrandCode());
        snapshot.put("depositSpread", spreadDefault.getDepositSpread());
        snapshot.put("withdrawSpread", spreadDefault.getWithdrawSpread());
        return snapshot;
    }

    private static BigDecimal asBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(value.toString());
    }
}
