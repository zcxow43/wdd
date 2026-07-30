package pl.piomin.services.backend.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import pl.piomin.services.backend.audit.AuditActionType;
import pl.piomin.services.backend.audit.AuditHandler;
import pl.piomin.services.backend.audit.AuditRequest;
import pl.piomin.services.backend.audit.AuditRequestMapper;
import pl.piomin.services.backend.exception.DuplicatePendingSpreadGroupCreateException;
import pl.piomin.services.backend.mapper.SpreadGroupMemberMapper;
import pl.piomin.services.backend.model.Brand;
import pl.piomin.services.backend.model.CurrencyPair;
import pl.piomin.services.backend.model.SpreadGroup;
import pl.piomin.services.backend.model.SpreadGroupMember;

/**
 * Plugs {@code spread_group}/{@code spread_group_member} create/update/delete
 * into the generic audit module (specs/backend/audit.md) as
 * {@code entityType = "SPREAD_GROUP"}. Reuses {@link SpreadGroupValidator}
 * for brand-existence, name-uniqueness, non-negative spreads, and
 * currencyPairIds no-duplicates/existence/brand-match, and reuses
 * {@link SpreadGroupService} itself to actually apply an approved change.
 */
@Component
public class SpreadGroupAuditHandler implements AuditHandler {

    public static final String ENTITY_TYPE = "SPREAD_GROUP";

    private final SpreadGroupService spreadGroupService;
    private final SpreadGroupValidator validator;
    private final SpreadGroupMemberMapper spreadGroupMemberMapper;
    private final AuditRequestMapper auditRequestMapper;
    private final ObjectMapper objectMapper;

    public SpreadGroupAuditHandler(SpreadGroupService spreadGroupService, SpreadGroupValidator validator,
                                    SpreadGroupMemberMapper spreadGroupMemberMapper,
                                    AuditRequestMapper auditRequestMapper, ObjectMapper objectMapper) {
        this.spreadGroupService = spreadGroupService;
        this.validator = validator;
        this.spreadGroupMemberMapper = spreadGroupMemberMapper;
        this.auditRequestMapper = auditRequestMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public String entityType() {
        return ENTITY_TYPE;
    }

    @Override
    public Map<String, Object> snapshotOf(Long entityId) {
        SpreadGroup group = spreadGroupService.getById(entityId);
        List<SpreadGroupMember> members = spreadGroupService.getMembers(entityId);
        return toSnapshot(group, members);
    }

    @Override
    public void validate(AuditActionType actionType, Long entityId, Map<String, Object> afterSnapshot) {
        // AuditService calls validate(...) twice over a request's lifetime: once at
        // submission (before the row is persisted) and once more to re-validate at
        // approval time. A fresh submission's snapshot has not yet been enriched
        // with brandCode below; the persisted/re-deserialized one at approval time
        // already has it. Use that as the signal for whether this is the original
        // submission, so the CREATE natural-key dedup check does not spuriously
        // match this very request against itself - same judgment call as
        // CurrencyPairAuditHandler (specs/backend/currency-pair-approval.md).
        boolean isOriginalSubmission = !afterSnapshot.containsKey("brandCode");

        Long brandId = asLong(afterSnapshot.get("brandId"));
        Brand brand = validator.validateBrandExists(brandId);

        String name = (String) afterSnapshot.get("name");
        validator.validateName(name);

        BigDecimal depositSpread = asBigDecimal(afterSnapshot.get("depositSpread"));
        BigDecimal withdrawSpread = asBigDecimal(afterSnapshot.get("withdrawSpread"));
        validator.validateSpreadNonNegative(depositSpread, withdrawSpread);

        List<Long> currencyPairIds;
        if (afterSnapshot.containsKey("currencyPairIds")) {
            currencyPairIds = asLongList(afterSnapshot.get("currencyPairIds"));
        } else {
            // UPDATE submitted with currencyPairIds omitted - freeze the group's
            // current live membership into the proposed snapshot so apply(...)
            // is a no-op for membership, per specs/backend/spread.md.
            currencyPairIds = spreadGroupMemberMapper.findByGroupId(entityId).stream()
                    .map(SpreadGroupMember::getCurrencyPairId).toList();
        }
        List<CurrencyPair> members = validator.validateMembers(brandId, currencyPairIds);

        validator.validateUniqueName(brandId, name, entityId);

        afterSnapshot.put("brandCode", brand.getCode());
        afterSnapshot.put("currencyPairIds", currencyPairIds);
        afterSnapshot.put("members", toMemberMaps(members));

        if (actionType == AuditActionType.CREATE && isOriginalSubmission) {
            checkNoPendingCreateDuplicate(brandId, name);
        }
    }

    @Override
    public Long apply(AuditActionType actionType, Long entityId, Map<String, Object> afterSnapshot) {
        return switch (actionType) {
            case CREATE -> spreadGroupService.create(
                    asLong(afterSnapshot.get("brandId")),
                    (String) afterSnapshot.get("name"),
                    asBigDecimal(afterSnapshot.get("depositSpread")),
                    asBigDecimal(afterSnapshot.get("withdrawSpread")),
                    asLongList(afterSnapshot.get("currencyPairIds"))).getId();
            case UPDATE -> {
                spreadGroupService.update(entityId, (String) afterSnapshot.get("name"),
                        asBigDecimal(afterSnapshot.get("depositSpread")),
                        asBigDecimal(afterSnapshot.get("withdrawSpread")),
                        afterSnapshot.containsKey("currencyPairIds")
                                ? asLongList(afterSnapshot.get("currencyPairIds")) : null);
                yield entityId;
            }
            case DELETE -> {
                spreadGroupService.delete(entityId);
                yield entityId;
            }
        };
    }

    @Override
    public String summarize(Map<String, Object> snapshot) {
        return snapshot.get("brandCode") + " · " + snapshot.get("name");
    }

    /**
     * This handler's own responsibility per specs/backend/audit.md: the generic
     * audit module can only dedup UPDATE/DELETE on (entityType, entityId); for
     * CREATE there is no entityId yet, so the natural-key (brandId, name) dedup
     * against other PENDING CREATE requests belongs here.
     */
    private void checkNoPendingCreateDuplicate(Long brandId, String name) {
        List<AuditRequest> pendingCreates = auditRequestMapper.findAll(ENTITY_TYPE, "PENDING", "CREATE");
        for (AuditRequest candidate : pendingCreates) {
            Map<String, Object> candidateAfter = parseSnapshot(candidate.getAfterSnapshot());
            if (candidateAfter == null) {
                continue;
            }
            if (Objects.equals(asLong(candidateAfter.get("brandId")), brandId)
                    && Objects.equals(candidateAfter.get("name"), name)) {
                throw new DuplicatePendingSpreadGroupCreateException(brandId, name);
            }
        }
    }

    private Map<String, Object> toSnapshot(SpreadGroup group, List<SpreadGroupMember> members) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("brandId", group.getBrandId());
        snapshot.put("brandCode", group.getBrandCode());
        snapshot.put("name", group.getName());
        snapshot.put("depositSpread", group.getDepositSpread());
        snapshot.put("withdrawSpread", group.getWithdrawSpread());
        snapshot.put("currencyPairIds", members.stream().map(SpreadGroupMember::getCurrencyPairId).toList());
        List<Map<String, Object>> memberMaps = new ArrayList<>();
        for (SpreadGroupMember member : members) {
            Map<String, Object> memberMap = new LinkedHashMap<>();
            memberMap.put("currencyPairId", member.getCurrencyPairId());
            memberMap.put("baseCurrencyCode", member.getBaseCurrencyCode());
            memberMap.put("quoteCurrencyCode", member.getQuoteCurrencyCode());
            memberMaps.add(memberMap);
        }
        snapshot.put("members", memberMaps);
        return snapshot;
    }

    private List<Map<String, Object>> toMemberMaps(List<CurrencyPair> pairs) {
        List<Map<String, Object>> memberMaps = new ArrayList<>();
        for (CurrencyPair pair : pairs) {
            Map<String, Object> memberMap = new LinkedHashMap<>();
            memberMap.put("currencyPairId", pair.getId());
            memberMap.put("baseCurrencyCode", pair.getBaseCurrencyCode());
            memberMap.put("quoteCurrencyCode", pair.getQuoteCurrencyCode());
            memberMaps.add(memberMap);
        }
        return memberMaps;
    }

    private Map<String, Object> parseSnapshot(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to deserialize audit snapshot", e);
        }
    }

    private static Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(value.toString());
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

    @SuppressWarnings("unchecked")
    private static List<Long> asLongList(Object value) {
        if (value == null) {
            return new ArrayList<>();
        }
        List<Object> raw = (List<Object>) value;
        List<Long> result = new ArrayList<>();
        for (Object item : raw) {
            result.add(asLong(item));
        }
        return result;
    }
}
