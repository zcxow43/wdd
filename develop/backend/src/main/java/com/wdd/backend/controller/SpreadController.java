package com.wdd.backend.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wdd.backend.audit.AuditActionType;
import com.wdd.backend.audit.AuditRequestResponse;
import com.wdd.backend.audit.AuditService;
import com.wdd.backend.dto.SpreadDefaultResponse;
import com.wdd.backend.dto.SpreadDefaultUpdateRequest;
import com.wdd.backend.dto.SpreadGroupCreateRequest;
import com.wdd.backend.dto.SpreadGroupDeleteRequest;
import com.wdd.backend.dto.SpreadGroupResponse;
import com.wdd.backend.dto.SpreadGroupUpdateRequest;
import com.wdd.backend.dto.SpreadResolutionResponse;
import com.wdd.backend.service.SpreadDefaultAuditHandler;
import com.wdd.backend.service.SpreadDefaultService;
import com.wdd.backend.service.SpreadGroupAuditHandler;
import com.wdd.backend.service.SpreadGroupService;

import jakarta.validation.Valid;

/**
 * Hosts both {@code /api/spread-defaults} and {@code /api/spread-groups} (plus the
 * {@code /api/spread-groups/resolve/{currencyPairId}} resolver) in one controller, per
 * specs/backend/spread.md's "New controller: SpreadController". {@code GET}s call the read-path
 * services directly (unaffected by the audit workflow — always live, already-approved data);
 * every mutation ({@code PUT} on spread-defaults; {@code POST}/{@code PUT}/{@code DELETE} on
 * spread-groups) submits an audit request through {@link AuditService} instead, returning
 * {@code 202 Accepted} with the resulting {@link AuditRequestResponse}. There is intentionally no
 * {@code POST}/{@code DELETE} for {@code /api/spread-defaults} — a {@code spread_default} row is
 * seeded 1:1 per brand and never created/removed through the API.
 */
@RestController
public class SpreadController {

    private static final String SPREAD_DEFAULT_ENTITY_TYPE = "SPREAD_DEFAULT";
    private static final String SPREAD_GROUP_ENTITY_TYPE = "SPREAD_GROUP";

    private final SpreadDefaultService spreadDefaultService;
    private final SpreadGroupService spreadGroupService;
    private final AuditService auditService;

    public SpreadController(SpreadDefaultService spreadDefaultService, SpreadGroupService spreadGroupService,
            AuditService auditService) {
        this.spreadDefaultService = spreadDefaultService;
        this.spreadGroupService = spreadGroupService;
        this.auditService = auditService;
    }

    // ---------- /api/spread-defaults ----------

    @GetMapping("/api/spread-defaults")
    public List<SpreadDefaultResponse> listDefaults(@RequestParam(required = false) Long brandId) {
        return spreadDefaultService.list(brandId);
    }

    @GetMapping("/api/spread-defaults/{id}")
    public SpreadDefaultResponse getDefaultById(@PathVariable Long id) {
        return spreadDefaultService.getById(id);
    }

    /**
     * Builds the proposed {@code after} map from the request body and submits it as a
     * {@code SPREAD_DEFAULT}/{@code UPDATE} audit request — {@link AuditService#submit} itself
     * calls {@link SpreadDefaultAuditHandler#snapshotOf} for the {@code before} snapshot. Nothing
     * is persisted to {@code spread_default} directly.
     */
    @PutMapping("/api/spread-defaults/{id}")
    public ResponseEntity<AuditRequestResponse> updateDefault(@PathVariable Long id,
            @Valid @RequestBody SpreadDefaultUpdateRequest request) {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("depositSpread", request.getDepositSpread());
        after.put("withdrawSpread", request.getWithdrawSpread());

        AuditRequestResponse response = auditService.submit(SPREAD_DEFAULT_ENTITY_TYPE, AuditActionType.UPDATE, id,
                after, request.getRequestedBy());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    // ---------- /api/spread-groups ----------

    @GetMapping("/api/spread-groups")
    public List<SpreadGroupResponse> listGroups(@RequestParam(required = false) Long brandId) {
        return spreadGroupService.list(brandId);
    }

    @GetMapping("/api/spread-groups/{id}")
    public SpreadGroupResponse getGroupById(@PathVariable Long id) {
        return spreadGroupService.getById(id);
    }

    /**
     * Builds the proposed {@code after} map (raw ids only — {@link SpreadGroupAuditHandler}
     * enriches it with {@code brandCode}/{@code members} during {@code validate}) and submits it
     * as a {@code SPREAD_GROUP}/{@code CREATE} audit request. Nothing is inserted into
     * {@code spread_group}/{@code spread_group_member} until approved.
     */
    @PostMapping("/api/spread-groups")
    public ResponseEntity<AuditRequestResponse> createGroup(@Valid @RequestBody SpreadGroupCreateRequest request) {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("brandId", request.getBrandId());
        after.put("name", request.getName());
        after.put("depositSpread", request.getDepositSpread());
        after.put("withdrawSpread", request.getWithdrawSpread());
        after.put("currencyPairIds",
                request.getCurrencyPairIds() != null ? request.getCurrencyPairIds() : new ArrayList<>());

        AuditRequestResponse response = auditService.submit(SPREAD_GROUP_ENTITY_TYPE, AuditActionType.CREATE, null,
                after, request.getRequestedBy());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * Reads the group's current values, merges the partial request onto them (same
     * merge-then-submit pattern as {@code CurrencyPairController.update}), and submits the
     * merged map as a {@code SPREAD_GROUP}/{@code UPDATE} audit request. {@code currencyPairIds}
     * is only added to the map when the caller actually supplied a replacement list — when
     * omitted, the key is left absent so {@link SpreadGroupAuditHandler#validate} knows to freeze
     * the group's current live membership instead ("omitted means unchanged").
     */
    @PutMapping("/api/spread-groups/{id}")
    public ResponseEntity<AuditRequestResponse> updateGroup(@PathVariable Long id,
            @RequestBody SpreadGroupUpdateRequest request) {
        SpreadGroupResponse current = spreadGroupService.getById(id);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("brandId", current.getBrandId());
        after.put("name", request.getName() != null ? request.getName() : current.getName());
        after.put("depositSpread",
                request.getDepositSpread() != null ? request.getDepositSpread() : current.getDepositSpread());
        after.put("withdrawSpread",
                request.getWithdrawSpread() != null ? request.getWithdrawSpread() : current.getWithdrawSpread());
        if (request.getCurrencyPairIds() != null) {
            after.put("currencyPairIds", request.getCurrencyPairIds());
        }

        AuditRequestResponse response = auditService.submit(SPREAD_GROUP_ENTITY_TYPE, AuditActionType.UPDATE, id,
                after, request.getRequestedBy());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @DeleteMapping("/api/spread-groups/{id}")
    public ResponseEntity<AuditRequestResponse> deleteGroup(@PathVariable Long id,
            @RequestBody(required = false) SpreadGroupDeleteRequest request) {
        String requestedBy = request != null ? request.getRequestedBy() : null;
        AuditRequestResponse response = auditService.submit(SPREAD_GROUP_ENTITY_TYPE, AuditActionType.DELETE, id,
                null, requestedBy);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * Always reads live, already-approved data (via {@link SpreadGroupService#resolveEffectiveSpread})
     * — a PENDING proposal never affects this endpoint's result.
     */
    @GetMapping("/api/spread-groups/resolve/{currencyPairId}")
    public SpreadResolutionResponse resolve(@PathVariable Long currencyPairId) {
        return spreadGroupService.resolveEffectiveSpread(currencyPairId);
    }
}
