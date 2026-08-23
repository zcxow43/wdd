package com.wdd.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wdd.backend.dto.AuditPendingResponse;
import com.wdd.backend.dto.SpreadGroupCreateRequest;
import com.wdd.backend.dto.SpreadGroupDetailResponse;
import com.wdd.backend.dto.SpreadGroupMemberAssignRequest;
import com.wdd.backend.dto.SpreadGroupResponse;
import com.wdd.backend.dto.SpreadGroupUpdateRequest;
import com.wdd.backend.service.SpreadGroupService;

@RestController
public class SpreadGroupController {

    private final SpreadGroupService spreadGroupService;

    public SpreadGroupController(SpreadGroupService spreadGroupService) {
        this.spreadGroupService = spreadGroupService;
    }

    @GetMapping("/api/spread-groups")
    public List<SpreadGroupResponse> listSpreadGroups(@RequestParam(required = false) Long brandId) {
        return spreadGroupService.findAll(brandId);
    }

    @GetMapping("/api/spread-groups/{id}")
    public SpreadGroupDetailResponse getSpreadGroup(@PathVariable Long id) {
        return spreadGroupService.findById(id);
    }

    @PostMapping("/api/spread-groups")
    public ResponseEntity<AuditPendingResponse> createSpreadGroup(@RequestBody SpreadGroupCreateRequest request,
            @RequestHeader(value = "X-Actor", required = false) String actor) {
        AuditPendingResponse pending = spreadGroupService.create(request, actor);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(pending);
    }

    @PutMapping("/api/spread-groups/{id}")
    public ResponseEntity<AuditPendingResponse> updateSpreadGroup(@PathVariable Long id,
            @RequestBody SpreadGroupUpdateRequest request,
            @RequestHeader(value = "X-Actor", required = false) String actor) {
        AuditPendingResponse pending = spreadGroupService.update(id, request, actor);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(pending);
    }

    @DeleteMapping("/api/spread-groups/{id}")
    public ResponseEntity<AuditPendingResponse> deleteSpreadGroup(@PathVariable Long id,
            @RequestHeader(value = "X-Actor", required = false) String actor) {
        AuditPendingResponse pending = spreadGroupService.delete(id, actor);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(pending);
    }

    @PostMapping("/api/spread-groups/{id}/members")
    public ResponseEntity<AuditPendingResponse> assignMembers(@PathVariable Long id,
            @RequestBody SpreadGroupMemberAssignRequest request,
            @RequestHeader(value = "X-Actor", required = false) String actor) {
        AuditPendingResponse pending = spreadGroupService.assignMembers(id, request, actor);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(pending);
    }

    @DeleteMapping("/api/spread-groups/{id}/members/{currencyPairId}")
    public ResponseEntity<AuditPendingResponse> removeMember(@PathVariable Long id,
            @PathVariable Long currencyPairId,
            @RequestHeader(value = "X-Actor", required = false) String actor) {
        AuditPendingResponse pending = spreadGroupService.removeMember(id, currencyPairId, actor);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(pending);
    }
}
