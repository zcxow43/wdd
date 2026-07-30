package pl.piomin.services.backend.controller;

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

import jakarta.validation.Valid;
import pl.piomin.services.backend.audit.AuditActionType;
import pl.piomin.services.backend.audit.AuditRequest;
import pl.piomin.services.backend.audit.AuditRequestResponse;
import pl.piomin.services.backend.audit.AuditService;
import pl.piomin.services.backend.dto.SpreadDefaultResponse;
import pl.piomin.services.backend.dto.SpreadDefaultUpdateRequest;
import pl.piomin.services.backend.dto.SpreadGroupCreateRequest;
import pl.piomin.services.backend.dto.SpreadGroupDeleteRequest;
import pl.piomin.services.backend.dto.SpreadGroupResponse;
import pl.piomin.services.backend.dto.SpreadGroupUpdateRequest;
import pl.piomin.services.backend.dto.SpreadResolutionResponse;
import pl.piomin.services.backend.model.SpreadGroup;
import pl.piomin.services.backend.service.SpreadDefaultAuditHandler;
import pl.piomin.services.backend.service.SpreadDefaultService;
import pl.piomin.services.backend.service.SpreadGroupAuditHandler;
import pl.piomin.services.backend.service.SpreadGroupService;

/**
 * GET endpoints (default spread list/get, spread group list/get, and the
 * effective-spread resolver) keep reading live, already-approved rows
 * directly and are unaffected by the audit-approval workflow. Every mutation
 * on either concept - the default spread's PUT, and the spread group's
 * POST/PUT/DELETE - submits a change request through the generic audit
 * module (specs/backend/audit.md) via {@link AuditService#submit} instead of
 * mutating directly, per specs/backend/spread.md.
 */
@RestController
public class SpreadController {

    private final SpreadDefaultService spreadDefaultService;
    private final SpreadGroupService spreadGroupService;
    private final AuditService auditService;

    public SpreadController(SpreadDefaultService spreadDefaultService, SpreadGroupService spreadGroupService,
                             AuditService auditService) {
        this.spreadDefaultService = spreadDefaultService;
        this.spreadGroupService = spreadGroupService;
        this.auditService = auditService;
    }

    // --- Default spread (/api/spread-defaults) --------------------------------

    @GetMapping("/api/spread-defaults")
    public List<SpreadDefaultResponse> listDefaults(@RequestParam(required = false) Long brandId) {
        return spreadDefaultService.list(brandId).stream()
                .map(SpreadDefaultResponse::from)
                .toList();
    }

    @GetMapping("/api/spread-defaults/{id}")
    public SpreadDefaultResponse getDefault(@PathVariable Long id) {
        return SpreadDefaultResponse.from(spreadDefaultService.getById(id));
    }

    @PutMapping("/api/spread-defaults/{id}")
    public ResponseEntity<AuditRequestResponse> updateDefault(@PathVariable Long id,
            @Valid @RequestBody SpreadDefaultUpdateRequest request) {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("depositSpread", request.getDepositSpread());
        after.put("withdrawSpread", request.getWithdrawSpread());

        AuditRequest auditRequest = auditService.submit(SpreadDefaultAuditHandler.ENTITY_TYPE, AuditActionType.UPDATE,
                id, after, request.getRequestedBy());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(AuditRequestResponse.from(auditRequest));
    }

    // --- Custom spread groups (/api/spread-groups) -----------------------------

    @GetMapping("/api/spread-groups")
    public List<SpreadGroupResponse> listGroups(@RequestParam(required = false) Long brandId) {
        return spreadGroupService.list(brandId).stream()
                .map(group -> SpreadGroupResponse.from(group, spreadGroupService.getMembers(group.getId())))
                .toList();
    }

    @GetMapping("/api/spread-groups/{id}")
    public SpreadGroupResponse getGroup(@PathVariable Long id) {
        SpreadGroup group = spreadGroupService.getById(id);
        return SpreadGroupResponse.from(group, spreadGroupService.getMembers(id));
    }

    @GetMapping("/api/spread-groups/resolve/{currencyPairId}")
    public SpreadResolutionResponse resolve(@PathVariable Long currencyPairId) {
        return spreadGroupService.resolveEffectiveSpread(currencyPairId);
    }

    @PostMapping("/api/spread-groups")
    public ResponseEntity<AuditRequestResponse> createGroup(@Valid @RequestBody SpreadGroupCreateRequest request) {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("brandId", request.getBrandId());
        after.put("name", request.getName());
        after.put("depositSpread", request.getDepositSpread());
        after.put("withdrawSpread", request.getWithdrawSpread());
        after.put("currencyPairIds", request.getCurrencyPairIds() != null ? request.getCurrencyPairIds() : List.of());

        AuditRequest auditRequest = auditService.submit(SpreadGroupAuditHandler.ENTITY_TYPE, AuditActionType.CREATE,
                null, after, request.getRequestedBy());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(AuditRequestResponse.from(auditRequest));
    }

    @PutMapping("/api/spread-groups/{id}")
    public ResponseEntity<AuditRequestResponse> updateGroup(@PathVariable Long id,
            @Valid @RequestBody SpreadGroupUpdateRequest request) {
        // Merge the partial request onto the group's current values so the
        // proposed "after" snapshot submitted for approval is self-contained,
        // per specs/backend/spread.md (same pattern as CurrencyPairController.update).
        SpreadGroup existing = spreadGroupService.getById(id);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("brandId", existing.getBrandId());
        after.put("name", request.getName() != null ? request.getName() : existing.getName());
        after.put("depositSpread", request.getDepositSpread() != null
                ? request.getDepositSpread() : existing.getDepositSpread());
        after.put("withdrawSpread", request.getWithdrawSpread() != null
                ? request.getWithdrawSpread() : existing.getWithdrawSpread());
        // Omitted entirely (null) means "leave membership unchanged" - only put the
        // key when the caller actually supplied a replacement list.
        if (request.getCurrencyPairIds() != null) {
            after.put("currencyPairIds", request.getCurrencyPairIds());
        }

        AuditRequest auditRequest = auditService.submit(SpreadGroupAuditHandler.ENTITY_TYPE, AuditActionType.UPDATE,
                id, after, request.getRequestedBy());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(AuditRequestResponse.from(auditRequest));
    }

    @DeleteMapping("/api/spread-groups/{id}")
    public ResponseEntity<AuditRequestResponse> deleteGroup(@PathVariable Long id,
            @RequestBody(required = false) SpreadGroupDeleteRequest request) {
        String requestedBy = request != null ? request.getRequestedBy() : null;
        AuditRequest auditRequest = auditService.submit(SpreadGroupAuditHandler.ENTITY_TYPE, AuditActionType.DELETE,
                id, null, requestedBy);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(AuditRequestResponse.from(auditRequest));
    }
}
