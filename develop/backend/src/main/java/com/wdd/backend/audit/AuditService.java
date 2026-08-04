package com.wdd.backend.audit;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Generic, entity-agnostic approval workflow service. Holds a registry of {@link AuditHandler}
 * beans keyed by {@link AuditHandler#entityType()} and never references any specific entity
 * type in its own code — all entity-specific behavior is delegated to the handler for the
 * request's {@code entityType}.
 */
@Service
public class AuditService {

    private final AuditRequestMapper auditRequestMapper;
    private final Map<String, AuditHandler> handlers;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AuditService(AuditRequestMapper auditRequestMapper, List<AuditHandler> handlerList) {
        this.auditRequestMapper = auditRequestMapper;
        this.handlers = handlerList.stream()
                .collect(Collectors.toMap(AuditHandler::entityType, h -> h));
    }

    public List<AuditRequestResponse> list(String entityType, String status, String actionType) {
        return auditRequestMapper.findAll(entityType, status, actionType).stream()
                .map(AuditRequestResponse::from)
                .collect(Collectors.toList());
    }

    public AuditRequestResponse getById(Long id) {
        AuditRequest auditRequest = findRequiredById(id);
        return AuditRequestResponse.from(auditRequest);
    }

    public Optional<AuditRequest> findPendingByEntity(String entityType, Long entityId) {
        return auditRequestMapper.findPendingByEntity(entityType, entityId);
    }

    @Transactional
    public AuditRequestResponse submit(String entityType, AuditActionType actionType, Long entityId,
                                        Map<String, Object> afterSnapshot, String requestedBy) {
        AuditHandler handler = getHandler(entityType);

        Map<String, Object> beforeSnapshot = null;
        if (actionType != AuditActionType.CREATE) {
            beforeSnapshot = handler.snapshotOf(entityId);
            findPendingByEntity(entityType, entityId).ifPresent(existing -> {
                throw new DuplicatePendingAuditRequestException(entityType, entityId);
            });
        }

        if (actionType != AuditActionType.DELETE) {
            handler.validate(actionType, entityId, afterSnapshot);
        }

        String summary = handler.summarize(beforeSnapshot != null ? beforeSnapshot : afterSnapshot);

        AuditRequest auditRequest = new AuditRequest();
        auditRequest.setEntityType(entityType);
        auditRequest.setActionType(actionType.name());
        auditRequest.setEntityId(entityId);
        auditRequest.setBeforeSnapshot(writeJson(beforeSnapshot));
        auditRequest.setAfterSnapshot(writeJson(afterSnapshot));
        auditRequest.setSummary(summary);
        auditRequest.setStatus(AuditStatus.PENDING.name());
        auditRequest.setRequestedBy(requestedBy);

        auditRequestMapper.insert(auditRequest);

        AuditRequest created = findRequiredById(auditRequest.getId());
        return AuditRequestResponse.from(created);
    }

    @Transactional
    public AuditRequestResponse approve(Long id, String reviewedBy) {
        AuditRequest auditRequest = findRequiredById(id);
        requirePending(auditRequest);

        AuditHandler handler = getHandler(auditRequest.getEntityType());
        AuditActionType actionType = AuditActionType.valueOf(auditRequest.getActionType());
        Long entityId = auditRequest.getEntityId();
        Map<String, Object> afterSnapshot = readJson(auditRequest.getAfterSnapshot());

        if (actionType != AuditActionType.DELETE) {
            handler.validate(actionType, entityId, afterSnapshot);
        }

        Long resultId = handler.apply(actionType, entityId, afterSnapshot);

        auditRequest.setEntityId(resultId);
        auditRequest.setStatus(AuditStatus.APPROVED.name());
        auditRequest.setReviewedBy(reviewedBy);
        auditRequest.setReviewedAt(LocalDateTime.now());

        auditRequestMapper.update(auditRequest);

        AuditRequest updated = findRequiredById(id);
        return AuditRequestResponse.from(updated);
    }

    @Transactional
    public AuditRequestResponse reject(Long id, String reviewedBy, String rejectReason) {
        AuditRequest auditRequest = findRequiredById(id);
        requirePending(auditRequest);

        auditRequest.setStatus(AuditStatus.REJECTED.name());
        auditRequest.setReviewedBy(reviewedBy);
        auditRequest.setReviewedAt(LocalDateTime.now());
        auditRequest.setRejectReason(rejectReason);

        auditRequestMapper.update(auditRequest);

        AuditRequest updated = findRequiredById(id);
        return AuditRequestResponse.from(updated);
    }

    private AuditRequest findRequiredById(Long id) {
        return auditRequestMapper.findById(id)
                .orElseThrow(() -> new AuditRequestNotFoundException(id));
    }

    private void requirePending(AuditRequest auditRequest) {
        if (!AuditStatus.PENDING.name().equals(auditRequest.getStatus())) {
            throw new AuditRequestAlreadyReviewedException(auditRequest.getId(), auditRequest.getStatus());
        }
    }

    private AuditHandler getHandler(String entityType) {
        AuditHandler handler = handlers.get(entityType);
        if (handler == null) {
            throw new IllegalStateException("No AuditHandler registered for entityType: " + entityType);
        }
        return handler;
    }

    private String writeJson(Map<String, Object> value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize audit snapshot to JSON", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readJson(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse stored audit snapshot JSON", e);
        }
    }
}
