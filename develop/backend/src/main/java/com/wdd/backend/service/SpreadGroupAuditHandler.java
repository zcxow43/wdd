package com.wdd.backend.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wdd.backend.audit.AuditActionType;
import com.wdd.backend.audit.AuditHandler;
import com.wdd.backend.audit.AuditRequest;
import com.wdd.backend.audit.AuditRequestMapper;
import com.wdd.backend.audit.AuditStatus;
import com.wdd.backend.dto.SpreadGroupMemberResponse;
import com.wdd.backend.dto.SpreadGroupResponse;
import com.wdd.backend.exception.CurrencyPairNotFoundException;
import com.wdd.backend.exception.DuplicatePendingSpreadGroupCreateException;
import com.wdd.backend.mapper.CurrencyPairMapper;
import com.wdd.backend.mapper.SpreadGroupMemberMapper;
import com.wdd.backend.model.Brand;
import com.wdd.backend.model.CurrencyPair;
import com.wdd.backend.model.SpreadGroupMember;

/**
 * {@link AuditHandler} plug-in for {@code entityType = "SPREAD_GROUP"} (specs/backend/spread.md).
 * Handles {@code CREATE}/{@code UPDATE}/{@code DELETE}; {@code validate} is never invoked for
 * {@code DELETE} (per {@link AuditHandler}'s own contract).
 */
@Component
public class SpreadGroupAuditHandler implements AuditHandler {

    public static final String ENTITY_TYPE = "SPREAD_GROUP";

    private final SpreadGroupService spreadGroupService;
    private final SpreadGroupValidator validator;
    private final SpreadGroupMemberMapper spreadGroupMemberMapper;
    private final CurrencyPairMapper currencyPairMapper;
    private final AuditRequestMapper auditRequestMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SpreadGroupAuditHandler(SpreadGroupService spreadGroupService, SpreadGroupValidator validator,
            SpreadGroupMemberMapper spreadGroupMemberMapper,
            CurrencyPairMapper currencyPairMapper, AuditRequestMapper auditRequestMapper) {
        this.spreadGroupService = spreadGroupService;
        this.validator = validator;
        this.spreadGroupMemberMapper = spreadGroupMemberMapper;
        this.currencyPairMapper = currencyPairMapper;
        this.auditRequestMapper = auditRequestMapper;
    }

    @Override
    public String entityType() {
        return ENTITY_TYPE;
    }

    @Override
    public Map<String, Object> snapshotOf(Long entityId) {
        SpreadGroupResponse group = spreadGroupService.getById(entityId);

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("brandId", group.getBrandId());
        snapshot.put("brandCode", group.getBrandCode());
        snapshot.put("name", group.getName());
        snapshot.put("depositSpread", group.getDepositSpread());
        snapshot.put("withdrawSpread", group.getWithdrawSpread());

        List<Long> currencyPairIds = group.getMembers().stream()
                .map(SpreadGroupMemberResponse::getCurrencyPairId)
                .collect(Collectors.toList());
        snapshot.put("currencyPairIds", currencyPairIds);

        List<Map<String, Object>> members = group.getMembers().stream()
                .map(this::toMemberMap)
                .collect(Collectors.toList());
        snapshot.put("members", members);

        return snapshot;
    }

    /**
     * Delegates brand-existence, name-uniqueness-within-brand (excluding {@code entityId} for
     * UPDATE), non-negative spreads, and currencyPairIds no-duplicates/existence/brand-match to
     * {@link SpreadGroupValidator}; enriches {@code after} in place with {@code brandCode}/
     * {@code members} (matching {@code CurrencyPairAuditHandler}'s enrichment pattern). For
     * {@code CREATE} only, and only on the *original* submission — detected via the same
     * self-collision-avoidance technique as {@code CurrencyPairAuditHandler}'s historical CREATE
     * dedup check (specs/backend/currency-pair-approval.md): a freshly submitted snapshot has not
     * yet been enriched with {@code brandCode}, so checking {@code
     * !afterSnapshot.containsKey("brandCode")} reliably distinguishes "original submission" from
     * "re-validation at approval time" (where {@code AuditService.approve()} re-invokes {@code
     * validate} against the request's own still-PENDING row) — checks no PENDING
     * SPREAD_GROUP/CREATE request already exists for the same (brandId, name).
     */
    @Override
    public void validate(AuditActionType actionType, Long entityId, Map<String, Object> afterSnapshot) {
        boolean isFreshCreateSubmission = actionType == AuditActionType.CREATE
                && !afterSnapshot.containsKey("brandCode");

        Long brandId = toLong(afterSnapshot.get("brandId"));
        String name = (String) afterSnapshot.get("name");
        BigDecimal depositSpread = toBigDecimal(afterSnapshot.get("depositSpread"));
        BigDecimal withdrawSpread = toBigDecimal(afterSnapshot.get("withdrawSpread"));

        Brand brand = validator.requireBrandExists(brandId);
        validator.requireNameValid(name);
        validator.requireSpreadNonNegative(depositSpread, withdrawSpread);
        validator.requireNameUniqueWithinBrand(brandId, name, entityId);

        List<Long> currencyPairIds = resolveCurrencyPairIds(actionType, entityId, afterSnapshot);
        validator.requireValidMembers(brandId, currencyPairIds);

        afterSnapshot.put("brandId", brandId);
        afterSnapshot.put("brandCode", brand.getCode());
        afterSnapshot.put("name", name);
        afterSnapshot.put("depositSpread", depositSpread);
        afterSnapshot.put("withdrawSpread", withdrawSpread);
        afterSnapshot.put("currencyPairIds", currencyPairIds);
        afterSnapshot.put("members", buildMembers(currencyPairIds));

        if (isFreshCreateSubmission && hasPendingCreateForBrandAndName(brandId, name)) {
            throw new DuplicatePendingSpreadGroupCreateException(brandId, name);
        }
    }

    /**
     * {@code CREATE}: inserts the group then attaches each proposed member (detaching it from
     * any prior group first). {@code UPDATE}: persists name/spreads, and — since {@code
     * currencyPairIds} is always populated by {@code validate} (either the caller's replacement
     * list, or the group's frozen live membership when omitted) — always replaces the full
     * membership set via {@code SpreadGroupService.update}'s three-way diff. {@code DELETE}:
     * removes memberships then the group row.
     */
    @Override
    public Long apply(AuditActionType actionType, Long entityId, Map<String, Object> afterSnapshot) {
        switch (actionType) {
            case CREATE: {
                Long brandId = toLong(afterSnapshot.get("brandId"));
                String name = (String) afterSnapshot.get("name");
                BigDecimal depositSpread = toBigDecimal(afterSnapshot.get("depositSpread"));
                BigDecimal withdrawSpread = toBigDecimal(afterSnapshot.get("withdrawSpread"));
                List<Long> currencyPairIds = toLongList(afterSnapshot.get("currencyPairIds"));

                SpreadGroupResponse created = spreadGroupService.create(brandId, name, depositSpread,
                        withdrawSpread, currencyPairIds);
                return created.getId();
            }
            case UPDATE: {
                String name = (String) afterSnapshot.get("name");
                BigDecimal depositSpread = toBigDecimal(afterSnapshot.get("depositSpread"));
                BigDecimal withdrawSpread = toBigDecimal(afterSnapshot.get("withdrawSpread"));
                List<Long> currencyPairIds = toLongList(afterSnapshot.get("currencyPairIds"));

                spreadGroupService.update(entityId, name, depositSpread, withdrawSpread, currencyPairIds);
                return entityId;
            }
            case DELETE:
                spreadGroupService.delete(entityId);
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
        return snapshot.get("brandCode") + " · " + snapshot.get("name");
    }

    private List<Long> resolveCurrencyPairIds(AuditActionType actionType, Long entityId,
            Map<String, Object> afterSnapshot) {
        if (afterSnapshot.containsKey("currencyPairIds")) {
            return toLongList(afterSnapshot.get("currencyPairIds"));
        }
        if (actionType == AuditActionType.UPDATE && entityId != null) {
            // currencyPairIds omitted from a PUT means "leave membership unchanged" — freeze the
            // group's current live membership into the persisted snapshot.
            return spreadGroupMemberMapper.findByGroupId(entityId).stream()
                    .map(SpreadGroupMember::getCurrencyPairId)
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    private List<Map<String, Object>> buildMembers(List<Long> currencyPairIds) {
        List<Map<String, Object>> members = new ArrayList<>();
        for (Long currencyPairId : currencyPairIds) {
            CurrencyPair pair = currencyPairMapper.findById(currencyPairId)
                    .orElseThrow(() -> new CurrencyPairNotFoundException(currencyPairId));
            Map<String, Object> member = new LinkedHashMap<>();
            member.put("currencyPairId", currencyPairId);
            member.put("baseCurrencyCode", pair.getBaseCurrencyCode());
            member.put("quoteCurrencyCode", pair.getQuoteCurrencyCode());
            members.add(member);
        }
        return members;
    }

    private Map<String, Object> toMemberMap(SpreadGroupMemberResponse member) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("currencyPairId", member.getCurrencyPairId());
        map.put("baseCurrencyCode", member.getBaseCurrencyCode());
        map.put("quoteCurrencyCode", member.getQuoteCurrencyCode());
        return map;
    }

    private boolean hasPendingCreateForBrandAndName(Long brandId, String name) {
        List<AuditRequest> pendingCreates = auditRequestMapper.findAll(ENTITY_TYPE, AuditStatus.PENDING.name(),
                AuditActionType.CREATE.name());
        for (AuditRequest request : pendingCreates) {
            Map<String, Object> snapshot = readJson(request.getAfterSnapshot());
            if (snapshot == null) {
                continue;
            }
            Long candidateBrandId = toLong(snapshot.get("brandId"));
            String candidateName = (String) snapshot.get("name");
            if (brandId.equals(candidateBrandId) && name.equals(candidateName)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readJson(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse stored audit snapshot JSON", e);
        }
    }

    private List<Long> toLongList(Object value) {
        List<Long> result = new ArrayList<>();
        if (value == null) {
            return result;
        }
        for (Object item : (List<?>) value) {
            result.add(toLong(item));
        }
        return result;
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
