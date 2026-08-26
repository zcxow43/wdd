package com.wdd.backend.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.wdd.backend.dto.AuditPendingResponse;
import com.wdd.backend.dto.Brand;
import com.wdd.backend.dto.CurrencyPair;
import com.wdd.backend.dto.CurrencyPairCreateRequest;
import com.wdd.backend.dto.CurrencyPairDefinition;
import com.wdd.backend.dto.CurrencyPairResponse;
import com.wdd.backend.dto.CurrencyPairUpdateRequest;
import com.wdd.backend.exception.CurrencyPairConflictException;
import com.wdd.backend.exception.CurrencyPairNotFoundException;
import com.wdd.backend.exception.InvalidRequestException;
import com.wdd.backend.mapper.BrandMapper;
import com.wdd.backend.mapper.CurrencyPairDefinitionMapper;
import com.wdd.backend.mapper.CurrencyPairMapper;

/**
 * Brand-scoped CRUD for {@code currency_pair} rows. Most rows are created by
 * {@link CurrencyPairDefinitionService}'s fan-out; this service additionally
 * supports creating/deleting individual rows directly.
 *
 * <p>Every {@code create}/{@code update}/{@code delete} here validates
 * exactly as before (immediate {@code 400}/{@code 404}/{@code 409}) but,
 * instead of writing, submits an audited change via {@link AuditService}
 * and returns the resulting pending request. The real write is performed
 * only on approval, by {@link CurrencyPairAuditHandler}. The definition
 * fan-out ({@link CurrencyPairDefinitionService}) writes {@link CurrencyPairMapper}
 * directly and is untouched by this — it is not a user action on this
 * entity.
 */
@Service
public class CurrencyPairService {

    private static final String ENTITY_TYPE = "CURRENCY_PAIR";
    private static final String ACTION_CREATE = "CREATE";
    private static final String ACTION_UPDATE = "UPDATE";
    private static final String ACTION_DELETE = "DELETE";
    private static final String RATE_TYPE_AUTO = "AUTO";
    private static final String RATE_TYPE_MANUAL = "MANUAL";

    private final CurrencyPairMapper currencyPairMapper;
    private final CurrencyPairDefinitionMapper currencyPairDefinitionMapper;
    private final BrandMapper brandMapper;
    private final AuditService auditService;

    public CurrencyPairService(CurrencyPairMapper currencyPairMapper,
            CurrencyPairDefinitionMapper currencyPairDefinitionMapper, BrandMapper brandMapper,
            AuditService auditService) {
        this.currencyPairMapper = currencyPairMapper;
        this.currencyPairDefinitionMapper = currencyPairDefinitionMapper;
        this.brandMapper = brandMapper;
        this.auditService = auditService;
    }

    public List<CurrencyPairResponse> findAll(Long currencyPairDefinitionId, Long brandId, Boolean active) {
        return currencyPairMapper.findAll(currencyPairDefinitionId, brandId, active).stream()
                .map(CurrencyPairService::toResponse)
                .toList();
    }

    public CurrencyPairResponse findById(Long id) {
        CurrencyPair currencyPair = currencyPairMapper.findById(id);
        if (currencyPair == null) {
            throw new CurrencyPairNotFoundException(id);
        }
        return toResponse(currencyPair);
    }

    public AuditPendingResponse create(CurrencyPairCreateRequest request, String actor) {
        if (request == null) {
            throw new InvalidRequestException("request body is required");
        }

        Long currencyPairDefinitionId = request.getCurrencyPairDefinitionId();
        Long brandId = request.getBrandId();
        if (currencyPairDefinitionId == null) {
            throw new InvalidRequestException("currencyPairDefinitionId is required");
        }
        if (brandId == null) {
            throw new InvalidRequestException("brandId is required");
        }

        CurrencyPairDefinition definition = currencyPairDefinitionMapper.findById(currencyPairDefinitionId);
        if (definition == null) {
            throw new InvalidRequestException(
                    "currencyPairDefinitionId does not reference an existing currency pair definition");
        }
        Brand brand = brandMapper.findById(brandId);
        if (brand == null) {
            throw new InvalidRequestException("brandId does not reference an existing brand");
        }

        if (currencyPairMapper.findByDefinitionAndBrand(currencyPairDefinitionId, brandId) != null) {
            throw new CurrencyPairConflictException(currencyPairDefinitionId, brandId);
        }

        String rateType = normalizeRateType(request.getRateType());
        BigDecimal rate = validateAndResolveRate(rateType, request.getRate(), definition.getPrecision());

        Boolean active = request.getActive();
        if (active == null) {
            active = false;
        }

        Map<String, Object> afterData = new LinkedHashMap<>();
        afterData.put("currencyPairDefinitionId", currencyPairDefinitionId);
        afterData.put("brandId", brandId);
        afterData.put("rateType", rateType);
        afterData.put("rate", rate);
        afterData.put("active", active);

        String summary = buildCreateSummary(brand.getCode(), definition, rateType, rate);

        var submitted = auditService.submit(ENTITY_TYPE, ACTION_CREATE, null, brandId, summary, null, afterData,
                actor);
        return AuditPendingResponse.from(submitted);
    }

    public AuditPendingResponse update(Long id, CurrencyPairUpdateRequest request, String actor) {
        if (request == null) {
            throw new InvalidRequestException("request body is required");
        }

        CurrencyPair existing = currencyPairMapper.findById(id);
        if (existing == null) {
            throw new CurrencyPairNotFoundException(id);
        }

        CurrencyPairDefinition definition =
                currencyPairDefinitionMapper.findById(existing.getCurrencyPairDefinitionId());

        String rateType = request.getRateType() != null
                ? normalizeRateType(request.getRateType())
                : existing.getRateType();
        BigDecimal requestedRate = request.getRate() != null ? request.getRate() : existing.getRate();
        BigDecimal rate = validateAndResolveRate(rateType, requestedRate, definition.getPrecision());

        Boolean active = request.getActive() != null ? request.getActive() : existing.getActive();

        Map<String, Object> beforeData = new LinkedHashMap<>();
        beforeData.put("currencyPairDefinitionId", existing.getCurrencyPairDefinitionId());
        beforeData.put("brandId", existing.getBrandId());
        beforeData.put("rateType", existing.getRateType());
        beforeData.put("rate", existing.getRate());
        beforeData.put("active", existing.getActive());

        Map<String, Object> afterData = new LinkedHashMap<>();
        afterData.put("currencyPairDefinitionId", existing.getCurrencyPairDefinitionId());
        afterData.put("brandId", existing.getBrandId());
        afterData.put("rateType", rateType);
        afterData.put("rate", rate);
        afterData.put("active", active);

        String summary = buildUpdateSummary(existing, rateType, rate, active);

        var submitted = auditService.submit(ENTITY_TYPE, ACTION_UPDATE, id, existing.getBrandId(), summary,
                beforeData, afterData, actor);
        return AuditPendingResponse.from(submitted);
    }

    public AuditPendingResponse delete(Long id, String actor) {
        CurrencyPair existing = currencyPairMapper.findById(id);
        if (existing == null) {
            throw new CurrencyPairNotFoundException(id);
        }

        Map<String, Object> beforeData = new LinkedHashMap<>();
        beforeData.put("currencyPairDefinitionId", existing.getCurrencyPairDefinitionId());
        beforeData.put("brandId", existing.getBrandId());
        beforeData.put("rateType", existing.getRateType());
        beforeData.put("rate", existing.getRate());
        beforeData.put("active", existing.getActive());

        String summary = buildDeleteSummary(existing);

        var submitted = auditService.submit(ENTITY_TYPE, ACTION_DELETE, id, existing.getBrandId(), summary,
                beforeData, null, actor);
        return AuditPendingResponse.from(submitted);
    }

    private static String normalizeRateType(String rateType) {
        if (rateType == null) {
            return RATE_TYPE_AUTO;
        }
        if (!RATE_TYPE_AUTO.equals(rateType) && !RATE_TYPE_MANUAL.equals(rateType)) {
            throw new InvalidRequestException("rateType must be AUTO or MANUAL");
        }
        return rateType;
    }

    private static BigDecimal validateAndResolveRate(String rateType, BigDecimal rate, Integer precision) {
        if (RATE_TYPE_AUTO.equals(rateType)) {
            return null;
        }
        if (rate == null) {
            throw new InvalidRequestException("rate is required when rateType is MANUAL");
        }
        if (rate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidRequestException("rate must be greater than 0");
        }
        int scale = Math.max(rate.stripTrailingZeros().scale(), 0);
        if (precision != null && scale > precision) {
            throw new InvalidRequestException("rate must not exceed " + precision + " decimal places");
        }
        return rate;
    }

    private static String buildCreateSummary(String brandCode, CurrencyPairDefinition definition, String rateType,
            BigDecimal rate) {
        String pairLabel = definition.getBaseCurrencyCode() + "/" + definition.getQuoteCurrencyCode();
        if (RATE_TYPE_MANUAL.equals(rateType)) {
            return brandCode + " " + pairLabel + " 新增幣種對，手動匯率 " + rate;
        }
        return brandCode + " " + pairLabel + " 新增幣種對";
    }

    private static String buildUpdateSummary(CurrencyPair existing, String rateType, BigDecimal rate,
            Boolean active) {
        String pairLabel = existing.getBaseCurrencyCode() + "/" + existing.getQuoteCurrencyCode();
        StringBuilder changes = new StringBuilder();
        boolean rateTypeChanged = !rateType.equals(existing.getRateType());
        boolean rateChanged = rateTypeChanged
                || (RATE_TYPE_MANUAL.equals(rateType)
                        && (existing.getRate() == null || existing.getRate().compareTo(rate) != 0));
        if (rateChanged) {
            if (RATE_TYPE_MANUAL.equals(rateType)) {
                changes.append("改為手動匯率 ").append(rate);
            } else {
                changes.append("改為自動匯率");
            }
        }
        if (!active.equals(existing.getActive())) {
            if (changes.length() > 0) {
                changes.append("，");
            }
            changes.append(Boolean.TRUE.equals(active) ? "啟用" : "停用");
        }
        if (changes.length() == 0) {
            changes.append("更新設定");
        }
        return existing.getBrandCode() + " " + pairLabel + " " + changes;
    }

    private static String buildDeleteSummary(CurrencyPair existing) {
        String pairLabel = existing.getBaseCurrencyCode() + "/" + existing.getQuoteCurrencyCode();
        return existing.getBrandCode() + " " + pairLabel + " 刪除幣種對";
    }

    /**
     * Maps a {@link CurrencyPair} row (already enriched by the shared read
     * query with {@code spreadGroupId}/{@code spreadGroupName}, the
     * {@code AUTO} base rate, and the resolved effective spreads) to its API
     * response shape, including the computed {@code depositRate}/
     * {@code withdrawalRate} ({@link CurrencyPair#getDepositRate()}/
     * {@link CurrencyPair#getWithdrawalRate()}). Package-private so {@link
     * CurrencyPairDefinitionService}'s fan-out response can reuse the exact
     * same mapping instead of duplicating it.
     */
    static CurrencyPairResponse toResponse(CurrencyPair currencyPair) {
        return new CurrencyPairResponse(
                currencyPair.getId(),
                currencyPair.getCurrencyPairDefinitionId(),
                currencyPair.getBaseCurrencyCode(),
                currencyPair.getQuoteCurrencyCode(),
                currencyPair.getBrandId(),
                currencyPair.getBrandCode(),
                currencyPair.getRateType(),
                currencyPair.getRate(),
                currencyPair.getActive(),
                currencyPair.getSpreadGroupId(),
                currencyPair.getSpreadGroupName(),
                currencyPair.getDepositRate(),
                currencyPair.getWithdrawalRate(),
                currencyPair.getCreatedAt(),
                currencyPair.getUpdatedAt());
    }
}
