package com.wdd.backend.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.wdd.backend.dto.AuditPendingResponse;
import com.wdd.backend.dto.Brand;
import com.wdd.backend.dto.CurrencyPair;
import com.wdd.backend.dto.SpreadGroup;
import com.wdd.backend.dto.SpreadGroupCreateRequest;
import com.wdd.backend.dto.SpreadGroupDetailResponse;
import com.wdd.backend.dto.SpreadGroupMemberAssignRequest;
import com.wdd.backend.dto.SpreadGroupMemberResponse;
import com.wdd.backend.dto.SpreadGroupResponse;
import com.wdd.backend.dto.SpreadGroupUpdateRequest;
import com.wdd.backend.exception.CurrencyPairBrandMismatchException;
import com.wdd.backend.exception.InvalidRequestException;
import com.wdd.backend.exception.SpreadGroupMemberConflictException;
import com.wdd.backend.exception.SpreadGroupMemberNotFoundException;
import com.wdd.backend.exception.SpreadGroupNameConflictException;
import com.wdd.backend.exception.SpreadGroupNotFoundException;
import com.wdd.backend.exception.UnknownCurrencyPairIdsException;
import com.wdd.backend.mapper.BrandMapper;
import com.wdd.backend.mapper.CurrencyPairMapper;
import com.wdd.backend.mapper.SpreadGroupMapper;

/**
 * CRUD on a brand's named spread groups (群組點差), plus moving brand
 * currency pairs in and out of them. A pair belongs to at most one group at
 * a time, structurally enforced by {@code currency_pair.spread_group_id}
 * being a single nullable column.
 *
 * <p>Every write here ({@code create}/{@code update}/{@code delete}/
 * {@code assignMembers}/{@code removeMember}) validates exactly as before
 * (immediate {@code 400}/{@code 404}/{@code 409}) but, instead of writing,
 * submits an audited change via {@link AuditService} and returns the
 * resulting pending request. The real write is performed only on approval,
 * by {@link SpreadGroupAuditHandler} (create/update/delete) or
 * {@link SpreadGroupMemberAuditHandler} (member add/remove). Group edits and
 * membership edits are tracked as separate {@code entityType}s
 * ({@code SPREAD_GROUP} vs {@code SPREAD_GROUP_MEMBER}) so one of each may
 * be pending for the same group at once; membership add and remove share
 * {@code SPREAD_GROUP_MEMBER}, keyed by the group's id, so only one
 * membership change may be pending per group at a time.
 */
@Service
public class SpreadGroupService {

    private static final int MAX_NAME_LENGTH = 50;

    private static final String ENTITY_TYPE_GROUP = "SPREAD_GROUP";
    private static final String ENTITY_TYPE_MEMBER = "SPREAD_GROUP_MEMBER";
    private static final String ACTION_CREATE = "CREATE";
    private static final String ACTION_UPDATE = "UPDATE";
    private static final String ACTION_DELETE = "DELETE";
    private static final String OPERATION_ADD = "ADD";
    private static final String OPERATION_REMOVE = "REMOVE";

    private final SpreadGroupMapper spreadGroupMapper;
    private final CurrencyPairMapper currencyPairMapper;
    private final BrandMapper brandMapper;
    private final AuditService auditService;

    public SpreadGroupService(SpreadGroupMapper spreadGroupMapper, CurrencyPairMapper currencyPairMapper,
            BrandMapper brandMapper, AuditService auditService) {
        this.spreadGroupMapper = spreadGroupMapper;
        this.currencyPairMapper = currencyPairMapper;
        this.brandMapper = brandMapper;
        this.auditService = auditService;
    }

    public List<SpreadGroupResponse> findAll(Long brandId) {
        return spreadGroupMapper.findAll(brandId).stream()
                .map(SpreadGroupService::toResponse)
                .toList();
    }

    public SpreadGroupDetailResponse findById(Long id) {
        SpreadGroup group = spreadGroupMapper.findById(id);
        if (group == null) {
            throw new SpreadGroupNotFoundException(id);
        }
        return toDetailResponse(group);
    }

    public AuditPendingResponse create(SpreadGroupCreateRequest request, String actor) {
        if (request == null) {
            throw new InvalidRequestException("request body is required");
        }

        Long brandId = request.getBrandId();
        if (brandId == null) {
            throw new InvalidRequestException("brandId is required");
        }
        Brand brand = brandMapper.findById(brandId);
        if (brand == null) {
            throw new InvalidRequestException("brandId does not reference an existing brand");
        }

        String name = normalizeName(request.getName());
        BigDecimal depositSpread = request.getDepositSpread() != null
                ? SpreadValidator.validate(request.getDepositSpread(), "depositSpread")
                : BigDecimal.ZERO;
        BigDecimal withdrawalSpread = request.getWithdrawalSpread() != null
                ? SpreadValidator.validate(request.getWithdrawalSpread(), "withdrawalSpread")
                : BigDecimal.ZERO;

        if (spreadGroupMapper.findByBrandAndName(brandId, name) != null) {
            throw new SpreadGroupNameConflictException();
        }

        Map<String, Object> afterData = new LinkedHashMap<>();
        afterData.put("brandId", brandId);
        afterData.put("name", name);
        afterData.put("depositSpread", depositSpread);
        afterData.put("withdrawalSpread", withdrawalSpread);

        String summary = String.format("%s 新增點差群組「%s」，入金 %s／出金 %s", brand.getCode(), name, depositSpread,
                withdrawalSpread);

        var submitted = auditService.submit(ENTITY_TYPE_GROUP, ACTION_CREATE, null, brandId, summary, null,
                afterData, actor);
        return AuditPendingResponse.from(submitted);
    }

    public AuditPendingResponse update(Long id, SpreadGroupUpdateRequest request, String actor) {
        if (request == null) {
            throw new InvalidRequestException("request body is required");
        }

        SpreadGroup existing = spreadGroupMapper.findById(id);
        if (existing == null) {
            throw new SpreadGroupNotFoundException(id);
        }

        String name = request.getName() != null ? normalizeName(request.getName()) : existing.getName();
        BigDecimal depositSpread = request.getDepositSpread() != null
                ? SpreadValidator.validate(request.getDepositSpread(), "depositSpread")
                : existing.getDepositSpread();
        BigDecimal withdrawalSpread = request.getWithdrawalSpread() != null
                ? SpreadValidator.validate(request.getWithdrawalSpread(), "withdrawalSpread")
                : existing.getWithdrawalSpread();

        if (!name.equals(existing.getName())) {
            SpreadGroup conflict = spreadGroupMapper.findByBrandAndName(existing.getBrandId(), name);
            if (conflict != null && !conflict.getId().equals(id)) {
                throw new SpreadGroupNameConflictException();
            }
        }

        Map<String, Object> beforeData = new LinkedHashMap<>();
        beforeData.put("name", existing.getName());
        beforeData.put("depositSpread", existing.getDepositSpread());
        beforeData.put("withdrawalSpread", existing.getWithdrawalSpread());

        Map<String, Object> afterData = new LinkedHashMap<>();
        afterData.put("name", name);
        afterData.put("depositSpread", depositSpread);
        afterData.put("withdrawalSpread", withdrawalSpread);

        String summary = buildUpdateSummary(existing, name, depositSpread, withdrawalSpread);

        var submitted = auditService.submit(ENTITY_TYPE_GROUP, ACTION_UPDATE, id, existing.getBrandId(), summary,
                beforeData, afterData, actor);
        return AuditPendingResponse.from(submitted);
    }

    public AuditPendingResponse delete(Long id, String actor) {
        SpreadGroup existing = spreadGroupMapper.findById(id);
        if (existing == null) {
            throw new SpreadGroupNotFoundException(id);
        }

        Map<String, Object> beforeData = new LinkedHashMap<>();
        beforeData.put("name", existing.getName());
        beforeData.put("depositSpread", existing.getDepositSpread());
        beforeData.put("withdrawalSpread", existing.getWithdrawalSpread());

        String summary = String.format("%s 刪除點差群組「%s」", existing.getBrandCode(), existing.getName());

        var submitted = auditService.submit(ENTITY_TYPE_GROUP, ACTION_DELETE, id, existing.getBrandId(), summary,
                beforeData, null, actor);
        return AuditPendingResponse.from(submitted);
    }

    public AuditPendingResponse assignMembers(Long groupId, SpreadGroupMemberAssignRequest request, String actor) {
        SpreadGroup group = spreadGroupMapper.findById(groupId);
        if (group == null) {
            throw new SpreadGroupNotFoundException(groupId);
        }

        List<Long> requestedIds = request != null ? request.getCurrencyPairIds() : null;
        if (requestedIds == null || requestedIds.isEmpty()) {
            throw new InvalidRequestException("currencyPairIds is required and must not be empty");
        }
        List<Long> ids = requestedIds.stream().distinct().toList();

        List<CurrencyPair> found = currencyPairMapper.findByIds(ids);
        Map<Long, CurrencyPair> byId = found.stream()
                .collect(Collectors.toMap(CurrencyPair::getId, Function.identity()));

        List<Long> unknown = ids.stream().filter(id -> !byId.containsKey(id)).toList();
        if (!unknown.isEmpty()) {
            throw new UnknownCurrencyPairIdsException(unknown);
        }

        List<Long> brandMismatch = ids.stream()
                .filter(id -> !byId.get(id).getBrandId().equals(group.getBrandId()))
                .toList();
        if (!brandMismatch.isEmpty()) {
            throw new CurrencyPairBrandMismatchException(brandMismatch);
        }

        List<Map<String, Object>> conflicts = new ArrayList<>();
        for (Long id : ids) {
            CurrencyPair pair = byId.get(id);
            Long currentGroupId = pair.getSpreadGroupId();
            if (currentGroupId != null && !currentGroupId.equals(groupId)) {
                Map<String, Object> conflict = new LinkedHashMap<>();
                conflict.put("currencyPairId", id);
                conflict.put("spreadGroupId", currentGroupId);
                conflict.put("spreadGroupName", pair.getSpreadGroupName());
                conflicts.add(conflict);
            }
        }
        if (!conflicts.isEmpty()) {
            throw new SpreadGroupMemberConflictException(conflicts);
        }

        List<Long> currentMemberIds = currencyPairMapper.findBySpreadGroupId(groupId).stream()
                .map(CurrencyPair::getId)
                .toList();

        Map<String, Object> beforeData = new LinkedHashMap<>();
        beforeData.put("currencyPairIds", currentMemberIds);

        Map<String, Object> afterData = new LinkedHashMap<>();
        afterData.put("operation", OPERATION_ADD);
        afterData.put("currencyPairIds", ids);

        String summary = String.format("%s 點差群組「%s」新增 %d 個幣種對", group.getBrandCode(), group.getName(),
                ids.size());

        var submitted = auditService.submit(ENTITY_TYPE_MEMBER, ACTION_UPDATE, groupId, group.getBrandId(), summary,
                beforeData, afterData, actor);
        return AuditPendingResponse.from(submitted);
    }

    public AuditPendingResponse removeMember(Long groupId, Long currencyPairId, String actor) {
        SpreadGroup group = spreadGroupMapper.findById(groupId);
        if (group == null) {
            throw new SpreadGroupNotFoundException(groupId);
        }
        CurrencyPair pair = currencyPairMapper.findById(currencyPairId);
        if (pair == null || !groupId.equals(pair.getSpreadGroupId())) {
            throw new SpreadGroupMemberNotFoundException(groupId, currencyPairId);
        }

        Map<String, Object> beforeData = new LinkedHashMap<>();
        beforeData.put("currencyPairIds", List.of(currencyPairId));

        Map<String, Object> afterData = new LinkedHashMap<>();
        afterData.put("operation", OPERATION_REMOVE);
        afterData.put("currencyPairIds", List.of(currencyPairId));

        String summary = String.format("%s 點差群組「%s」移除幣種對 %s/%s", group.getBrandCode(), group.getName(),
                pair.getBaseCurrencyCode(), pair.getQuoteCurrencyCode());

        var submitted = auditService.submit(ENTITY_TYPE_MEMBER, ACTION_UPDATE, groupId, group.getBrandId(), summary,
                beforeData, afterData, actor);
        return AuditPendingResponse.from(submitted);
    }

    private static String normalizeName(String name) {
        if (name == null) {
            throw new InvalidRequestException("name is required");
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_NAME_LENGTH) {
            throw new InvalidRequestException("name must be 1-50 characters after trimming");
        }
        return trimmed;
    }

    private static String buildUpdateSummary(SpreadGroup existing, String name, BigDecimal depositSpread,
            BigDecimal withdrawalSpread) {
        StringBuilder changes = new StringBuilder();
        if (!name.equals(existing.getName())) {
            changes.append("更名為「").append(name).append("」");
        }
        boolean depositChanged = existing.getDepositSpread() == null
                || existing.getDepositSpread().compareTo(depositSpread) != 0;
        boolean withdrawalChanged = existing.getWithdrawalSpread() == null
                || existing.getWithdrawalSpread().compareTo(withdrawalSpread) != 0;
        if (depositChanged || withdrawalChanged) {
            if (changes.length() > 0) {
                changes.append("，");
            }
            changes.append("點差改為入金 ").append(depositSpread).append("／出金 ").append(withdrawalSpread);
        }
        if (changes.length() == 0) {
            changes.append("更新設定");
        }
        return existing.getBrandCode() + " 點差群組「" + existing.getName() + "」" + changes;
    }

    private SpreadGroupDetailResponse toDetailResponse(SpreadGroup group) {
        List<SpreadGroupMemberResponse> members = currencyPairMapper.findBySpreadGroupId(group.getId()).stream()
                .map(SpreadGroupService::toMemberResponse)
                .toList();
        return new SpreadGroupDetailResponse(
                group.getId(),
                group.getBrandId(),
                group.getBrandCode(),
                group.getName(),
                group.getDepositSpread(),
                group.getWithdrawalSpread(),
                group.getMemberCount(),
                group.getCreatedAt(),
                group.getUpdatedAt(),
                members);
    }

    private static SpreadGroupResponse toResponse(SpreadGroup group) {
        return new SpreadGroupResponse(
                group.getId(),
                group.getBrandId(),
                group.getBrandCode(),
                group.getName(),
                group.getDepositSpread(),
                group.getWithdrawalSpread(),
                group.getMemberCount(),
                group.getCreatedAt(),
                group.getUpdatedAt());
    }

    private static SpreadGroupMemberResponse toMemberResponse(CurrencyPair pair) {
        return new SpreadGroupMemberResponse(
                pair.getId(),
                pair.getCurrencyPairDefinitionId(),
                pair.getBaseCurrencyCode(),
                pair.getQuoteCurrencyCode(),
                pair.getActive());
    }
}
