package com.wdd.backend.audit;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * Generic review-queue API usable by any {@code entityType} that has registered an
 * {@link AuditHandler}. Contains no entity-specific logic whatsoever.
 */
@RestController
@RequestMapping("/api/audit-requests")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public List<AuditRequestResponse> list(@RequestParam(required = false) String entityType,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(required = false) String actionType) {
        return auditService.list(entityType, status, actionType);
    }

    @GetMapping("/{id}")
    public AuditRequestResponse getById(@PathVariable Long id) {
        return auditService.getById(id);
    }

    @PostMapping("/{id}/approve")
    public AuditRequestResponse approve(@PathVariable Long id,
                                         @RequestBody(required = false) ApproveAuditRequestRequest request) {
        String reviewedBy = request != null ? request.getReviewedBy() : null;
        return auditService.approve(id, reviewedBy);
    }

    @PostMapping("/{id}/reject")
    public AuditRequestResponse reject(@PathVariable Long id,
                                        @Valid @RequestBody RejectAuditRequestRequest request) {
        return auditService.reject(id, request.getReviewedBy(), request.getRejectReason());
    }
}
