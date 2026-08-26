package com.wdd.backend.service;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.wdd.backend.dto.AuditRequest;
import com.wdd.backend.exception.AuditHandlerException;
import com.wdd.backend.mapper.BrandMapper;
import com.wdd.backend.mapper.BrandSpreadMapper;

/**
 * The {@code BRAND_SPREAD} {@link AuditHandler}. {@code entityId} is the
 * {@code brandId} — a brand has exactly one default spread row, never
 * created or deleted through this API, only updated. {@code validate}
 * re-checks the brand still exists and re-runs the same value checks
 * {@link BrandSpreadService#update} ran at submit time; {@code apply}
 * performs the real create-if-missing-then-update.
 */
@Component
public class BrandSpreadAuditHandler implements AuditHandler {

    private static final String ENTITY_TYPE = "BRAND_SPREAD";
    private static final String ACTION_UPDATE = "UPDATE";
    private static final int MAX_SCALE = 8;
    private static final BigDecimal MAX_VALUE = BigDecimal.valueOf(100);

    private final BrandSpreadMapper brandSpreadMapper;
    private final BrandMapper brandMapper;

    public BrandSpreadAuditHandler(BrandSpreadMapper brandSpreadMapper, BrandMapper brandMapper) {
        this.brandSpreadMapper = brandSpreadMapper;
        this.brandMapper = brandMapper;
    }

    @Override
    public String entityType() {
        return ENTITY_TYPE;
    }

    @Override
    public void validate(AuditRequest request) {
        if (!ACTION_UPDATE.equals(request.getActionType())) {
            throw new AuditHandlerException("Unknown actionType: " + request.getActionType());
        }
        Long brandId = request.getEntityId();
        if (brandId == null || brandMapper.findById(brandId) == null) {
            throw new AuditHandlerException("Brand " + brandId + " no longer exists");
        }

        Map<String, Object> after = asMap(request.getAfterData());
        validateSpreadValue(after.get("depositSpreadPercent"), "depositSpreadPercent");
        validateSpreadValue(after.get("withdrawalSpreadPercent"), "withdrawalSpreadPercent");
    }

    @Override
    public void apply(AuditRequest request) {
        Long brandId = request.getEntityId();
        Map<String, Object> after = asMap(request.getAfterData());
        BigDecimal depositSpreadPercent = toBigDecimal(after.get("depositSpreadPercent"));
        BigDecimal withdrawalSpreadPercent = toBigDecimal(after.get("withdrawalSpreadPercent"));

        if (brandSpreadMapper.findByBrandId(brandId) == null) {
            brandSpreadMapper.insertZero(brandId);
        }
        brandSpreadMapper.update(brandId, depositSpreadPercent, withdrawalSpreadPercent);
    }

    private static BigDecimal validateSpreadValue(Object raw, String fieldName) {
        BigDecimal value = toBigDecimal(raw);
        if (value == null) {
            throw new AuditHandlerException(fieldName + " is required");
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new AuditHandlerException(fieldName + " must be >= 0");
        }
        if (value.compareTo(MAX_VALUE) > 0) {
            throw new AuditHandlerException(fieldName + " must be <= 100");
        }
        int scale = Math.max(value.stripTrailingZeros().scale(), 0);
        if (scale > MAX_SCALE) {
            throw new AuditHandlerException(fieldName + " must not exceed " + MAX_SCALE + " decimal places");
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object data) {
        if (!(data instanceof Map)) {
            throw new AuditHandlerException("Expected a JSON object for beforeData/afterData");
        }
        return (Map<String, Object>) data;
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        return new BigDecimal(value.toString());
    }
}
