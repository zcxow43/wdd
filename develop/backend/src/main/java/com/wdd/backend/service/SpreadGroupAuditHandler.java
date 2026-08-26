package com.wdd.backend.service;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import com.wdd.backend.dto.AuditRequest;
import com.wdd.backend.dto.SpreadGroup;
import com.wdd.backend.exception.AuditHandlerException;
import com.wdd.backend.mapper.BrandMapper;
import com.wdd.backend.mapper.SpreadGroupMapper;

/**
 * The {@code SPREAD_GROUP} {@link AuditHandler} — create/rename/re-price a
 * named group, or delete one. {@code entityId} is the group's id
 * ({@code null} for a pending create). {@code validate} re-runs the same
 * existence/name-uniqueness/value checks {@link SpreadGroupService} ran at
 * submit time, but against data as it stands right now (the brand or group
 * may have been removed since, or the name may have been taken by another
 * approved request meanwhile). {@code apply} performs the real
 * insert/update/delete. Membership itself is a separate {@code entityType}
 * ({@code SPREAD_GROUP_MEMBER}, see {@link SpreadGroupMemberAuditHandler})
 * and is not touched here — group deletion relies on the FK's
 * {@code ON DELETE SET NULL} to null out members' {@code spread_group_id}.
 */
@Component
public class SpreadGroupAuditHandler implements AuditHandler {

    private static final String ENTITY_TYPE = "SPREAD_GROUP";
    private static final String ACTION_CREATE = "CREATE";
    private static final String ACTION_UPDATE = "UPDATE";
    private static final String ACTION_DELETE = "DELETE";
    private static final int MAX_NAME_LENGTH = 50;
    private static final int MAX_SCALE = 8;
    private static final BigDecimal MAX_VALUE = BigDecimal.valueOf(100);

    private final SpreadGroupMapper spreadGroupMapper;
    private final BrandMapper brandMapper;

    public SpreadGroupAuditHandler(SpreadGroupMapper spreadGroupMapper, BrandMapper brandMapper) {
        this.spreadGroupMapper = spreadGroupMapper;
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
        Long brandId = toLong(after.get("brandId"));
        if (brandId == null || brandMapper.findById(brandId) == null) {
            throw new AuditHandlerException("brandId no longer references an existing brand");
        }
        String name = validateName(after.get("name"));
        validateSpreadValue(after.get("depositSpreadPercent"), "depositSpreadPercent");
        validateSpreadValue(after.get("withdrawalSpreadPercent"), "withdrawalSpreadPercent");
        if (spreadGroupMapper.findByBrandAndName(brandId, name) != null) {
            throw new AuditHandlerException("Spread group name already exists for this brand");
        }
    }

    private void applyCreate(AuditRequest request) {
        Map<String, Object> after = asMap(request.getAfterData());
        SpreadGroup group = new SpreadGroup();
        group.setBrandId(toLong(after.get("brandId")));
        group.setName((String) after.get("name"));
        group.setDepositSpreadPercent(toBigDecimal(after.get("depositSpreadPercent")));
        group.setWithdrawalSpreadPercent(toBigDecimal(after.get("withdrawalSpreadPercent")));
        try {
            spreadGroupMapper.insert(group);
        } catch (DuplicateKeyException e) {
            throw new AuditHandlerException("Spread group name already exists for this brand");
        }
    }

    private void validateUpdate(AuditRequest request) {
        SpreadGroup existing = spreadGroupMapper.findById(request.getEntityId());
        if (existing == null) {
            throw new AuditHandlerException("Spread group " + request.getEntityId() + " no longer exists");
        }
        Map<String, Object> after = asMap(request.getAfterData());
        String name = validateName(after.get("name"));
        validateSpreadValue(after.get("depositSpreadPercent"), "depositSpreadPercent");
        validateSpreadValue(after.get("withdrawalSpreadPercent"), "withdrawalSpreadPercent");
        if (!name.equals(existing.getName())) {
            SpreadGroup conflict = spreadGroupMapper.findByBrandAndName(existing.getBrandId(), name);
            if (conflict != null && !conflict.getId().equals(existing.getId())) {
                throw new AuditHandlerException("Spread group name already exists for this brand");
            }
        }
    }

    private void applyUpdate(AuditRequest request) {
        Map<String, Object> after = asMap(request.getAfterData());
        SpreadGroup toUpdate = new SpreadGroup();
        toUpdate.setId(request.getEntityId());
        toUpdate.setName((String) after.get("name"));
        toUpdate.setDepositSpreadPercent(toBigDecimal(after.get("depositSpreadPercent")));
        toUpdate.setWithdrawalSpreadPercent(toBigDecimal(after.get("withdrawalSpreadPercent")));
        try {
            spreadGroupMapper.update(toUpdate);
        } catch (DuplicateKeyException e) {
            throw new AuditHandlerException("Spread group name already exists for this brand");
        }
    }

    private void validateDelete(AuditRequest request) {
        if (spreadGroupMapper.findById(request.getEntityId()) == null) {
            throw new AuditHandlerException("Spread group " + request.getEntityId() + " no longer exists");
        }
    }

    private void applyDelete(AuditRequest request) {
        // Members' spread_group_id becomes NULL via ON DELETE SET NULL — do not null them manually first.
        spreadGroupMapper.deleteById(request.getEntityId());
    }

    private static String validateName(Object raw) {
        if (!(raw instanceof String name) || name.trim().isEmpty() || name.trim().length() > MAX_NAME_LENGTH) {
            throw new AuditHandlerException("name must be 1-50 characters after trimming");
        }
        return name.trim();
    }

    private static void validateSpreadValue(Object raw, String fieldName) {
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
}
