package com.wdd.backend.service;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.wdd.backend.dto.AuditRequest;
import com.wdd.backend.dto.CurrencyPair;
import com.wdd.backend.dto.CurrencyPairDefinition;
import com.wdd.backend.exception.AuditHandlerException;
import com.wdd.backend.mapper.BrandMapper;
import com.wdd.backend.mapper.CurrencyPairDefinitionMapper;
import com.wdd.backend.mapper.CurrencyPairMapper;

/**
 * The {@code CURRENCY_PAIR} {@link AuditHandler} — the only place the audit
 * module learns anything about currency pairs (per [audit.md]'s handler
 * contract). {@code validate} re-runs the same existence/uniqueness/rate
 * precision checks {@link CurrencyPairService} already ran at submit time,
 * but against data as it stands right now: the parent definition's
 * {@code precision} may have tightened, the row may have been deleted since,
 * or the {@code (definition, brand)} slot may have been taken by another
 * approved request. {@code apply} performs the real insert/update/delete.
 *
 * <p>{@code beforeData}/{@code afterData} round-trip through the {@code
 * audit_request} table's JSON columns between submit and approval, so by the
 * time {@code validate}/{@code apply} run here they are plain {@code Map}s
 * with JSON-native value types (e.g. a decimal rate deserializes as a
 * {@code Double}, an id as an {@code Integer}/{@code Long}) rather than the
 * original {@code BigDecimal}/{@code Long} — {@link #toBigDecimal(Object)}/
 * {@link #toLong(Object)} normalize that back.
 */
@Component
public class CurrencyPairAuditHandler implements AuditHandler {

    private static final String ENTITY_TYPE = "CURRENCY_PAIR";
    private static final String ACTION_CREATE = "CREATE";
    private static final String ACTION_UPDATE = "UPDATE";
    private static final String ACTION_DELETE = "DELETE";
    private static final String RATE_TYPE_AUTO = "AUTO";
    private static final String RATE_TYPE_MANUAL = "MANUAL";

    private final CurrencyPairMapper currencyPairMapper;
    private final CurrencyPairDefinitionMapper currencyPairDefinitionMapper;
    private final BrandMapper brandMapper;

    public CurrencyPairAuditHandler(CurrencyPairMapper currencyPairMapper,
            CurrencyPairDefinitionMapper currencyPairDefinitionMapper, BrandMapper brandMapper) {
        this.currencyPairMapper = currencyPairMapper;
        this.currencyPairDefinitionMapper = currencyPairDefinitionMapper;
        this.brandMapper = brandMapper;
    }

    @Override
    public String entityType() {
        return ENTITY_TYPE;
    }

    @Override
    public void validate(AuditRequest request) {
        String actionType = request.getActionType();
        if (ACTION_CREATE.equals(actionType)) {
            validateCreate(request);
        } else if (ACTION_UPDATE.equals(actionType)) {
            validateUpdate(request);
        } else if (ACTION_DELETE.equals(actionType)) {
            validateDelete(request);
        } else {
            throw new AuditHandlerException("Unknown actionType: " + actionType);
        }
    }

    @Override
    public void apply(AuditRequest request) {
        String actionType = request.getActionType();
        if (ACTION_CREATE.equals(actionType)) {
            applyCreate(request);
        } else if (ACTION_UPDATE.equals(actionType)) {
            applyUpdate(request);
        } else if (ACTION_DELETE.equals(actionType)) {
            applyDelete(request);
        } else {
            throw new AuditHandlerException("Unknown actionType: " + actionType);
        }
    }

    private void validateCreate(AuditRequest request) {
        Map<String, Object> after = asMap(request.getAfterData());
        Long definitionId = toLong(after.get("currencyPairDefinitionId"));
        Long brandId = toLong(after.get("brandId"));

        CurrencyPairDefinition definition = currencyPairDefinitionMapper.findById(definitionId);
        if (definition == null) {
            throw new AuditHandlerException(
                    "currencyPairDefinitionId no longer references an existing currency pair definition");
        }
        if (brandMapper.findById(brandId) == null) {
            throw new AuditHandlerException("brandId no longer references an existing brand");
        }
        if (currencyPairMapper.findByDefinitionAndBrand(definitionId, brandId) != null) {
            throw new AuditHandlerException(
                    "A currency pair for this (currencyPairDefinitionId, brandId) already exists");
        }

        String rateType = toRateType(after.get("rateType"));
        BigDecimal rate = toBigDecimal(after.get("rate"));
        validateRate(rateType, rate, definition.getPrecision());
    }

    private void applyCreate(AuditRequest request) {
        Map<String, Object> after = asMap(request.getAfterData());
        String rateType = toRateType(after.get("rateType"));

        CurrencyPair currencyPair = new CurrencyPair();
        currencyPair.setCurrencyPairDefinitionId(toLong(after.get("currencyPairDefinitionId")));
        currencyPair.setBrandId(toLong(after.get("brandId")));
        currencyPair.setRateType(rateType);
        currencyPair.setRate(RATE_TYPE_AUTO.equals(rateType) ? null : toBigDecimal(after.get("rate")));
        currencyPair.setActive(toBoolean(after.get("active")));
        currencyPairMapper.insert(currencyPair);
    }

    private void validateUpdate(AuditRequest request) {
        CurrencyPair existing = currencyPairMapper.findById(request.getEntityId());
        if (existing == null) {
            throw new AuditHandlerException("Currency pair " + request.getEntityId() + " no longer exists");
        }
        CurrencyPairDefinition definition =
                currencyPairDefinitionMapper.findById(existing.getCurrencyPairDefinitionId());
        if (definition == null) {
            throw new AuditHandlerException("The parent currency pair definition no longer exists");
        }

        Map<String, Object> after = asMap(request.getAfterData());
        String rateType = toRateType(after.get("rateType"));
        BigDecimal rate = toBigDecimal(after.get("rate"));
        validateRate(rateType, rate, definition.getPrecision());
    }

    private void applyUpdate(AuditRequest request) {
        Map<String, Object> after = asMap(request.getAfterData());
        String rateType = toRateType(after.get("rateType"));

        CurrencyPair toUpdate = new CurrencyPair();
        toUpdate.setId(request.getEntityId());
        toUpdate.setRateType(rateType);
        toUpdate.setRate(RATE_TYPE_AUTO.equals(rateType) ? null : toBigDecimal(after.get("rate")));
        toUpdate.setActive(toBoolean(after.get("active")));
        currencyPairMapper.update(toUpdate);
    }

    private void validateDelete(AuditRequest request) {
        if (currencyPairMapper.findById(request.getEntityId()) == null) {
            throw new AuditHandlerException("Currency pair " + request.getEntityId() + " no longer exists");
        }
    }

    private void applyDelete(AuditRequest request) {
        currencyPairMapper.deleteById(request.getEntityId());
    }

    private static void validateRate(String rateType, BigDecimal rate, Integer precision) {
        if (RATE_TYPE_AUTO.equals(rateType)) {
            return;
        }
        if (rate == null) {
            throw new AuditHandlerException("rate is required when rateType is MANUAL");
        }
        if (rate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new AuditHandlerException("rate must be greater than 0");
        }
        int scale = Math.max(rate.stripTrailingZeros().scale(), 0);
        if (precision != null && scale > precision) {
            throw new AuditHandlerException("rate must not exceed " + precision + " decimal places");
        }
    }

    private static String toRateType(Object value) {
        if (value == null) {
            return RATE_TYPE_AUTO;
        }
        String rateType = value.toString();
        if (!RATE_TYPE_AUTO.equals(rateType) && !RATE_TYPE_MANUAL.equals(rateType)) {
            throw new AuditHandlerException("rateType must be AUTO or MANUAL");
        }
        return rateType;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object data) {
        if (!(data instanceof Map)) {
            throw new AuditHandlerException("Expected a JSON object for beforeData/afterData");
        }
        return (Map<String, Object>) data;
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(value.toString());
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

    private static Boolean toBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.valueOf(value.toString());
    }
}
