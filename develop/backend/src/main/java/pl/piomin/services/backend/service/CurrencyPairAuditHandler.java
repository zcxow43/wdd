package pl.piomin.services.backend.service;

import java.math.BigDecimal;
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
import pl.piomin.services.backend.dto.CurrencyPairCreateRequest;
import pl.piomin.services.backend.dto.CurrencyPairUpdateRequest;
import pl.piomin.services.backend.exception.BrandNotFoundException;
import pl.piomin.services.backend.exception.CurrencyNotFoundException;
import pl.piomin.services.backend.exception.CurrencyPairNotFoundException;
import pl.piomin.services.backend.exception.DuplicatePendingCurrencyPairCreateException;
import pl.piomin.services.backend.mapper.CurrencyPairMapper;
import pl.piomin.services.backend.model.Brand;
import pl.piomin.services.backend.model.Currency;
import pl.piomin.services.backend.model.CurrencyPair;

/**
 * Plugs {@code currency_pair} create/update/delete into the generic audit
 * module (specs/backend/audit.md) as {@code entityType = "CURRENCY_PAIR"}.
 * Reuses {@link CurrencyPairValidator} for the same brand/currency-existence,
 * base != quote, rate/rateType, and uniqueness rules already enforced by
 * {@link CurrencyPairService}, and reuses {@link CurrencyPairService} itself
 * to actually apply an approved change.
 */
@Component
public class CurrencyPairAuditHandler implements AuditHandler {

    public static final String ENTITY_TYPE = "CURRENCY_PAIR";

    private final CurrencyPairMapper currencyPairMapper;
    private final CurrencyPairValidator validator;
    private final CurrencyPairService currencyPairService;
    private final AuditRequestMapper auditRequestMapper;
    private final ObjectMapper objectMapper;

    public CurrencyPairAuditHandler(CurrencyPairMapper currencyPairMapper, CurrencyPairValidator validator,
                                     CurrencyPairService currencyPairService, AuditRequestMapper auditRequestMapper,
                                     ObjectMapper objectMapper) {
        this.currencyPairMapper = currencyPairMapper;
        this.validator = validator;
        this.currencyPairService = currencyPairService;
        this.auditRequestMapper = auditRequestMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public String entityType() {
        return ENTITY_TYPE;
    }

    @Override
    public Map<String, Object> snapshotOf(Long entityId) {
        CurrencyPair pair = currencyPairMapper.findById(entityId);
        if (pair == null) {
            throw new CurrencyPairNotFoundException(entityId);
        }
        return toSnapshot(pair);
    }

    @Override
    public void validate(AuditActionType actionType, Long entityId, Map<String, Object> afterSnapshot) {
        Long brandId = asLong(afterSnapshot.get("brandId"));
        Long baseCurrencyId = asLong(afterSnapshot.get("baseCurrencyId"));
        Long quoteCurrencyId = asLong(afterSnapshot.get("quoteCurrencyId"));

        // AuditService calls validate(...) twice over a request's lifetime: once
        // at submission (before the row is persisted) and once more to re-validate
        // at approval time (after deserializing the already-persisted snapshot).
        // A fresh submission's snapshot has not yet been enriched with codes below;
        // the persisted/re-deserialized one at approval time already has them. Use
        // that as the signal for whether this is the original submission, so the
        // CREATE natural-key dedup check below (which only makes sense pre-insertion)
        // does not spuriously match this very request against itself when the audit
        // module re-validates it at approval time.
        boolean isOriginalSubmission = !afterSnapshot.containsKey("brandCode");

        Brand brand = validator.getBrand(brandId);
        if (brand == null) {
            throw new BrandNotFoundException(brandId);
        }
        Currency baseCurrency = validator.getCurrency(baseCurrencyId);
        if (baseCurrency == null) {
            throw new CurrencyNotFoundException(baseCurrencyId);
        }
        Currency quoteCurrency = validator.getCurrency(quoteCurrencyId);
        if (quoteCurrency == null) {
            throw new CurrencyNotFoundException(quoteCurrencyId);
        }
        validator.validateDistinct(baseCurrencyId, quoteCurrencyId);
        validator.validateUnique(brandId, baseCurrencyId, quoteCurrencyId, entityId);

        // Apply the rate/rateType rule against a scratch entity, then write the
        // (possibly forced-to-null) effective rate back into the snapshot map so
        // both the persisted after_snapshot and the API response reflect it.
        CurrencyPair scratch = new CurrencyPair();
        scratch.setRateType((String) afterSnapshot.get("rateType"));
        validator.applyRateTypeRule(scratch, asBigDecimal(afterSnapshot.get("rate")), null);
        afterSnapshot.put("rate", scratch.getRate());

        // Resolve codes so before/after snapshots are self-contained, per the
        // snapshot shape documented in specs/backend/currency-pair-approval.md.
        afterSnapshot.put("brandCode", brand.getCode());
        afterSnapshot.put("baseCurrencyCode", baseCurrency.getCode());
        afterSnapshot.put("quoteCurrencyCode", quoteCurrency.getCode());

        if (actionType == AuditActionType.CREATE && isOriginalSubmission) {
            checkNoPendingCreateDuplicate(brandId, baseCurrencyId, quoteCurrencyId);
        }
    }

    @Override
    public Long apply(AuditActionType actionType, Long entityId, Map<String, Object> afterSnapshot) {
        return switch (actionType) {
            case CREATE -> currencyPairService.create(toCreateRequest(afterSnapshot)).getId();
            case UPDATE -> {
                currencyPairService.update(entityId, toUpdateRequest(afterSnapshot));
                yield entityId;
            }
            case DELETE -> {
                currencyPairService.delete(entityId);
                yield entityId;
            }
        };
    }

    @Override
    public String summarize(Map<String, Object> snapshot) {
        return snapshot.get("brandCode") + " · " + snapshot.get("baseCurrencyCode") + "/"
                + snapshot.get("quoteCurrencyCode");
    }

    /**
     * This handler's own responsibility per specs/backend/audit.md: the generic
     * audit module can only dedup UPDATE/DELETE on (entityType, entityId); for
     * CREATE there is no entityId yet, so the natural-key (brand, base, quote)
     * dedup against other PENDING CREATE requests belongs here.
     */
    private void checkNoPendingCreateDuplicate(Long brandId, Long baseCurrencyId, Long quoteCurrencyId) {
        List<AuditRequest> pendingCreates = auditRequestMapper.findAll(ENTITY_TYPE, "PENDING", "CREATE");
        for (AuditRequest candidate : pendingCreates) {
            Map<String, Object> candidateAfter = parseSnapshot(candidate.getAfterSnapshot());
            if (candidateAfter == null) {
                continue;
            }
            if (Objects.equals(asLong(candidateAfter.get("brandId")), brandId)
                    && Objects.equals(asLong(candidateAfter.get("baseCurrencyId")), baseCurrencyId)
                    && Objects.equals(asLong(candidateAfter.get("quoteCurrencyId")), quoteCurrencyId)) {
                throw new DuplicatePendingCurrencyPairCreateException(brandId, baseCurrencyId, quoteCurrencyId);
            }
        }
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

    private CurrencyPairCreateRequest toCreateRequest(Map<String, Object> snapshot) {
        CurrencyPairCreateRequest request = new CurrencyPairCreateRequest();
        request.setBrandId(asLong(snapshot.get("brandId")));
        request.setBaseCurrencyId(asLong(snapshot.get("baseCurrencyId")));
        request.setQuoteCurrencyId(asLong(snapshot.get("quoteCurrencyId")));
        request.setRate(asBigDecimal(snapshot.get("rate")));
        request.setRateType((String) snapshot.get("rateType"));
        request.setActive(asBoolean(snapshot.get("active")));
        return request;
    }

    private CurrencyPairUpdateRequest toUpdateRequest(Map<String, Object> snapshot) {
        CurrencyPairUpdateRequest request = new CurrencyPairUpdateRequest();
        request.setBrandId(asLong(snapshot.get("brandId")));
        request.setBaseCurrencyId(asLong(snapshot.get("baseCurrencyId")));
        request.setQuoteCurrencyId(asLong(snapshot.get("quoteCurrencyId")));
        request.setRate(asBigDecimal(snapshot.get("rate")));
        request.setRateType((String) snapshot.get("rateType"));
        request.setActive(asBoolean(snapshot.get("active")));
        return request;
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

    private static Boolean asBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.valueOf(value.toString());
    }
}
