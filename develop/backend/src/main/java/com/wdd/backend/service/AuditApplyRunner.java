package com.wdd.backend.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wdd.backend.dto.AuditRequest;
import com.wdd.backend.mapper.AuditRequestMapper;

/**
 * Runs the transactional core of approval: handler validate, handler apply,
 * and the status update, as one unit. Kept as a separate Spring bean (rather
 * than a private method on {@link AuditService}) so {@code @Transactional}
 * is honored via the proxy — a self-invoked private/internal method would
 * silently run outside a transaction.
 */
@Service
public class AuditApplyRunner {

    private final AuditRequestMapper auditRequestMapper;

    public AuditApplyRunner(AuditRequestMapper auditRequestMapper) {
        this.auditRequestMapper = auditRequestMapper;
    }

    @Transactional
    public void run(AuditRequest request, AuditHandler handler, String reviewedBy, String reviewComment) {
        handler.validate(request);
        handler.apply(request);
        auditRequestMapper.updateResolved(request.getId(), "APPROVED", reviewedBy, LocalDateTime.now(),
                reviewComment, null);
    }
}
