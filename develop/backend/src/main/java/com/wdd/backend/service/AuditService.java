package com.wdd.backend.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wdd.backend.dto.AuditActionRequest;
import com.wdd.backend.dto.AuditRequest;
import com.wdd.backend.dto.AuditRequestDetailResponse;
import com.wdd.backend.dto.AuditRequestSummaryResponse;
import com.wdd.backend.exception.AuditApplyFailedException;
import com.wdd.backend.exception.AuditHandlerException;
import com.wdd.backend.exception.AuditRequestConflictException;
import com.wdd.backend.exception.AuditRequestNotFoundException;
import com.wdd.backend.exception.InvalidRequestException;
import com.wdd.backend.mapper.AuditRequestMapper;

/**
 * The generic approval module. Knows nothing about any audited entity —
 * {@code beforeData}/{@code afterData} are opaque JSON here, and all
 * entity-specific behavior is delegated to the {@link AuditHandler}
 * resolved by {@code entityType} via {@link AuditHandlerRegistry}.
 */
@Service
public class AuditService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final int MAX_COMMENT_LENGTH = 500;
    private static final int MAX_SUMMARY_LENGTH = 200;

    private final AuditRequestMapper auditRequestMapper;
    private final AuditHandlerRegistry handlerRegistry;
    private final AuditApplyRunner applyRunner;

    public AuditService(AuditRequestMapper auditRequestMapper, AuditHandlerRegistry handlerRegistry,
            AuditApplyRunner applyRunner) {
        this.auditRequestMapper = auditRequestMapper;
        this.handlerRegistry = handlerRegistry;
        this.applyRunner = applyRunner;
    }

    public List<AuditRequestSummaryResponse> findAll(String status, String entityType, Long brandId,
            Long entityId) {
        return auditRequestMapper.findAll(status, entityType, brandId, entityId).stream()
                .map(AuditService::toSummaryResponse)
                .toList();
    }

    public AuditRequestDetailResponse findById(Long id) {
        return toDetailResponse(requireFound(id));
    }

    /**
     * The submit contract used by audited entity code — not exposed over
     * HTTP by this module. Performs no entity validation of its own; the
     * caller validates before submitting.
     */
    @Transactional
    public AuditRequest submit(String entityType, String actionType, Long entityId, Long brandId, String summary,
            Object beforeData, Object afterData, String requestedBy) {
        if (entityType == null || entityType.isBlank()) {
            throw new InvalidRequestException("entityType is required");
        }
        if (actionType == null || actionType.isBlank()) {
            throw new InvalidRequestException("actionType is required");
        }
        if (summary == null || summary.isBlank() || summary.length() > MAX_SUMMARY_LENGTH) {
            throw new InvalidRequestException("summary is required and must be at most 200 characters");
        }

        if (entityId != null && auditRequestMapper.findPending(entityType, entityId) != null) {
            throw AuditRequestConflictException.pendingExists(entityType, entityId);
        }

        AuditRequest request = new AuditRequest();
        request.setEntityType(entityType);
        request.setActionType(actionType);
        request.setEntityId(entityId);
        request.setBrandId(brandId);
        request.setSummary(summary);
        request.setBeforeData(beforeData);
        request.setAfterData(afterData);
        request.setStatus(STATUS_PENDING);
        request.setRequestedBy(resolveActor(requestedBy));

        try {
            auditRequestMapper.insert(request);
        } catch (DuplicateKeyException e) {
            // The service-level check above already covers the common case; this is the
            // last-line-of-defense path when the pending_key unique index catches a race.
            throw AuditRequestConflictException.pendingExists(entityType, entityId);
        }

        return auditRequestMapper.findById(request.getId());
    }

    /**
     * Loads the handler, then runs validate+apply+status-update as one
     * transaction in {@link AuditApplyRunner}. If the handler rejects the
     * change, the request stays {@code PENDING} with {@code applyError}
     * recorded in a separate, immediately-committed statement.
     */
    public AuditRequestDetailResponse approve(Long id, AuditActionRequest body, String actor) {
        AuditRequest existing = requireFound(id);
        requirePending(existing);

        AuditHandler handler = handlerRegistry.resolve(existing.getEntityType());
        String comment = trimToNull(body != null ? body.getComment() : null);
        validateCommentLength(comment);

        try {
            applyRunner.run(existing, handler, resolveActor(actor), comment);
        } catch (AuditHandlerException e) {
            auditRequestMapper.updateApplyError(id, e.getMessage());
            throw new AuditApplyFailedException(id, e.getMessage());
        }

        return toDetailResponse(auditRequestMapper.findById(id));
    }

    @Transactional
    public AuditRequestDetailResponse reject(Long id, AuditActionRequest body, String actor) {
        AuditRequest existing = requireFound(id);
        requirePending(existing);

        String comment = body != null ? body.getComment() : null;
        if (comment == null || comment.trim().isEmpty()) {
            throw new InvalidRequestException("comment is required");
        }
        comment = comment.trim();
        validateCommentLength(comment);

        auditRequestMapper.updateResolved(id, STATUS_REJECTED, resolveActor(actor), LocalDateTime.now(), comment,
                null);
        return toDetailResponse(auditRequestMapper.findById(id));
    }

    @Transactional
    public AuditRequestDetailResponse cancel(Long id, AuditActionRequest body, String actor) {
        AuditRequest existing = requireFound(id);
        requirePending(existing);

        String comment = trimToNull(body != null ? body.getComment() : null);
        validateCommentLength(comment);

        auditRequestMapper.updateResolved(id, STATUS_CANCELLED, resolveActor(actor), LocalDateTime.now(), comment,
                null);
        return toDetailResponse(auditRequestMapper.findById(id));
    }

    private AuditRequest requireFound(Long id) {
        AuditRequest request = auditRequestMapper.findById(id);
        if (request == null) {
            throw new AuditRequestNotFoundException(id);
        }
        return request;
    }

    private static void requirePending(AuditRequest request) {
        if (!STATUS_PENDING.equals(request.getStatus())) {
            throw AuditRequestConflictException.notPending(request.getId(), request.getStatus());
        }
    }

    private static void validateCommentLength(String comment) {
        if (comment != null && comment.length() > MAX_COMMENT_LENGTH) {
            throw new InvalidRequestException("comment must be at most 500 characters");
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String resolveActor(String actor) {
        return (actor == null || actor.isBlank()) ? "system" : actor;
    }

    private static AuditRequestSummaryResponse toSummaryResponse(AuditRequest r) {
        return new AuditRequestSummaryResponse(
                r.getId(), r.getEntityType(), r.getActionType(), r.getEntityId(), r.getBrandId(), r.getSummary(),
                r.getStatus(), r.getRequestedBy(), r.getRequestedAt(), r.getReviewedBy(), r.getReviewedAt(),
                r.getReviewComment(), r.getApplyError());
    }

    private static AuditRequestDetailResponse toDetailResponse(AuditRequest r) {
        return new AuditRequestDetailResponse(
                r.getId(), r.getEntityType(), r.getActionType(), r.getEntityId(), r.getBrandId(), r.getSummary(),
                r.getBeforeData(), r.getAfterData(), r.getStatus(), r.getRequestedBy(), r.getRequestedAt(),
                r.getReviewedBy(), r.getReviewedAt(), r.getReviewComment(), r.getApplyError());
    }
}
