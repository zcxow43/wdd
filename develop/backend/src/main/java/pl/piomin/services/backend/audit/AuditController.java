package pl.piomin.services.backend.audit;

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
 * Generic REST API for the audit/approval workflow. Usable by a review UI for
 * any entity type without the UI or this API needing entity-specific
 * endpoints. Contains no entity-specific logic whatsoever.
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
        return auditService.list(entityType, status, actionType).stream()
                .map(AuditRequestResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public AuditRequestResponse getById(@PathVariable Long id) {
        return AuditRequestResponse.from(auditService.getById(id));
    }

    @PostMapping("/{id}/approve")
    public AuditRequestResponse approve(@PathVariable Long id,
                                         @RequestBody(required = false) ApproveAuditRequestRequest request) {
        String reviewedBy = request != null ? request.getReviewedBy() : null;
        return AuditRequestResponse.from(auditService.approve(id, reviewedBy));
    }

    @PostMapping("/{id}/reject")
    public AuditRequestResponse reject(@PathVariable Long id,
                                        @Valid @RequestBody RejectAuditRequestRequest request) {
        return AuditRequestResponse.from(auditService.reject(id, request.getReviewedBy(), request.getRejectReason()));
    }
}
