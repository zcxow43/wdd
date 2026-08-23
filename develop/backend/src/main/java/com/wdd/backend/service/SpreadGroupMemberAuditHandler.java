package com.wdd.backend.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.wdd.backend.dto.AuditRequest;
import com.wdd.backend.dto.CurrencyPair;
import com.wdd.backend.dto.SpreadGroup;
import com.wdd.backend.exception.AuditHandlerException;
import com.wdd.backend.mapper.CurrencyPairMapper;
import com.wdd.backend.mapper.SpreadGroupMapper;

/**
 * The {@code SPREAD_GROUP_MEMBER} {@link AuditHandler} — moves brand
 * currency pairs in and out of a spread group. {@code entityId} is the
 * group's id for both an add batch ({@code afterData.operation == "ADD"})
 * and a single removal ({@code "REMOVE"}); the pending-request-per-target
 * rule on that shared id is what serializes membership edits per group.
 *
 * <p>{@code validate} re-runs the same all-or-nothing batch checks
 * {@link SpreadGroupService} ran at submit time, against data as it stands
 * right now — if any pair in the batch is no longer assignable (the group
 * was deleted, a pair was pulled into another group meanwhile, etc.), this
 * throws and {@link AuditApplyRunner} never calls {@link #apply}, so
 * nothing is written; that is the whole "all-or-nothing on apply" guarantee
 * from the spec. {@code apply} performs the real
 * {@code UPDATE currency_pair SET spread_group_id = ...}.
 */
@Component
public class SpreadGroupMemberAuditHandler implements AuditHandler {

    private static final String ENTITY_TYPE = "SPREAD_GROUP_MEMBER";
    private static final String ACTION_UPDATE = "UPDATE";
    private static final String OPERATION_ADD = "ADD";
    private static final String OPERATION_REMOVE = "REMOVE";

    private final SpreadGroupMapper spreadGroupMapper;
    private final CurrencyPairMapper currencyPairMapper;

    public SpreadGroupMemberAuditHandler(SpreadGroupMapper spreadGroupMapper, CurrencyPairMapper currencyPairMapper) {
        this.spreadGroupMapper = spreadGroupMapper;
        this.currencyPairMapper = currencyPairMapper;
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
        Long groupId = request.getEntityId();
        SpreadGroup group = spreadGroupMapper.findById(groupId);
        if (group == null) {
            throw new AuditHandlerException("Spread group " + groupId + " no longer exists");
        }

        Map<String, Object> after = asMap(request.getAfterData());
        String operation = requireOperation(after);
        List<Long> ids = toLongList(after.get("currencyPairIds"));

        if (OPERATION_ADD.equals(operation)) {
            validateAdd(group, ids);
        } else {
            validateRemove(groupId, ids);
        }
    }

    @Override
    public void apply(AuditRequest request) {
        Long groupId = request.getEntityId();
        Map<String, Object> after = asMap(request.getAfterData());
        String operation = requireOperation(after);
        List<Long> ids = toLongList(after.get("currencyPairIds"));

        if (OPERATION_ADD.equals(operation)) {
            applyAdd(groupId, ids);
        } else {
            applyRemove(groupId, ids);
        }
    }

    private void validateAdd(SpreadGroup group, List<Long> ids) {
        List<CurrencyPair> found = currencyPairMapper.findByIds(ids);
        Map<Long, CurrencyPair> byId = found.stream()
                .collect(Collectors.toMap(CurrencyPair::getId, Function.identity()));

        List<Long> unknown = ids.stream().filter(id -> !byId.containsKey(id)).toList();
        if (!unknown.isEmpty()) {
            throw new AuditHandlerException("Unknown currency pair ids: " + unknown);
        }

        List<Long> brandMismatch = ids.stream()
                .filter(id -> !byId.get(id).getBrandId().equals(group.getBrandId()))
                .toList();
        if (!brandMismatch.isEmpty()) {
            throw new AuditHandlerException("Currency pair belongs to a different brand: " + brandMismatch);
        }

        List<Long> conflicts = ids.stream()
                .filter(id -> {
                    Long currentGroupId = byId.get(id).getSpreadGroupId();
                    return currentGroupId != null && !currentGroupId.equals(group.getId());
                })
                .toList();
        if (!conflicts.isEmpty()) {
            throw new AuditHandlerException(
                    "Currency pair already belongs to another spread group: " + conflicts);
        }
    }

    private void applyAdd(Long groupId, List<Long> ids) {
        List<CurrencyPair> found = currencyPairMapper.findByIds(ids);
        List<Long> toAssign = found.stream()
                .filter(pair -> !groupId.equals(pair.getSpreadGroupId()))
                .map(CurrencyPair::getId)
                .toList();
        if (!toAssign.isEmpty()) {
            currencyPairMapper.updateSpreadGroupForIds(toAssign, groupId);
        }
    }

    private void validateRemove(Long groupId, List<Long> ids) {
        List<Long> notMembers = new ArrayList<>();
        for (Long id : ids) {
            CurrencyPair pair = currencyPairMapper.findById(id);
            if (pair == null || !groupId.equals(pair.getSpreadGroupId())) {
                notMembers.add(id);
            }
        }
        if (!notMembers.isEmpty()) {
            throw new AuditHandlerException(
                    "Currency pair is no longer a member of spread group " + groupId + ": " + notMembers);
        }
    }

    private void applyRemove(Long groupId, List<Long> ids) {
        for (Long id : ids) {
            currencyPairMapper.clearSpreadGroupIfMember(id, groupId);
        }
    }

    private static String requireOperation(Map<String, Object> after) {
        Object operation = after.get("operation");
        if (!(operation instanceof String value) || (!OPERATION_ADD.equals(value) && !OPERATION_REMOVE.equals(value))) {
            throw new AuditHandlerException("operation must be ADD or REMOVE");
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

    private static List<Long> toLongList(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            throw new AuditHandlerException("currencyPairIds is required and must not be empty");
        }
        List<Long> result = new ArrayList<>();
        for (Object item : list) {
            result.add(toLong(item));
        }
        return result;
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
}
