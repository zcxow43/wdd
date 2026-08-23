package com.wdd.backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wdd.backend.dto.AuditActionRequest;
import com.wdd.backend.dto.AuditRequestDetailResponse;
import com.wdd.backend.dto.AuditRequestSummaryResponse;
import com.wdd.backend.service.AuditService;

@RestController
@RequestMapping("/api/audit-requests")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public List<AuditRequestSummaryResponse> listAuditRequests(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) Long entityId) {
        return auditService.findAll(status, entityType, brandId, entityId);
    }

    @GetMapping("/{id}")
    public AuditRequestDetailResponse getAuditRequest(@PathVariable Long id) {
        return auditService.findById(id);
    }

    @PostMapping("/{id}/approve")
    public AuditRequestDetailResponse approve(@PathVariable Long id,
            @RequestBody(required = false) AuditActionRequest request,
            @RequestHeader(value = "X-Actor", required = false) String actor) {
        return auditService.approve(id, request, actor);
    }

    @PostMapping("/{id}/reject")
    public AuditRequestDetailResponse reject(@PathVariable Long id,
            @RequestBody(required = false) AuditActionRequest request,
            @RequestHeader(value = "X-Actor", required = false) String actor) {
        return auditService.reject(id, request, actor);
    }

    @PostMapping("/{id}/cancel")
    public AuditRequestDetailResponse cancel(@PathVariable Long id,
            @RequestBody(required = false) AuditActionRequest request,
            @RequestHeader(value = "X-Actor", required = false) String actor) {
        return auditService.cancel(id, request, actor);
    }
}
