package pl.piomin.services.backend.audit;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Generic, entity-agnostic approval service backing {@code /api/audit-requests}.
 * Holds a registry of {@link AuditHandler}s keyed by {@code entityType}
 * (populated by Spring from all {@link AuditHandler} beans) and never imports
 * or references any specific entity's classes. Adding a new approval-gated
 * feature requires implementing and registering a new {@link AuditHandler} —
 * nothing here ever changes.
 */
@Service
public class AuditService {

    private static final String PENDING = "PENDING";
    private static final String APPROVED = "APPROVED";
    private static final String REJECTED = "REJECTED";

    private final AuditRequestMapper auditRequestMapper;
    private final Map<String, AuditHandler> handlers;
    private final ObjectMapper objectMapper;

    public AuditService(AuditRequestMapper auditRequestMapper, List<AuditHandler> handlerList,
                         ObjectMapper objectMapper) {
        this.auditRequestMapper = auditRequestMapper;
        this.handlers = handlerList.stream()
                .collect(Collectors.toMap(AuditHandler::entityType, handler -> handler));
        this.objectMapper = objectMapper;
    }

    public List<AuditRequest> list(String entityType, String status, String actionType) {
        return auditRequestMapper.findAll(entityType, status, actionType);
    }

    public AuditRequest getById(Long id) {
        AuditRequest request = auditRequestMapper.findById(id);
        if (request == null) {
            throw new AuditRequestNotFoundException(id);
        }
        return request;
    }

    @Transactional
    public AuditRequest submit(String entityType, AuditActionType actionType, Long entityId,
                                Map<String, Object> afterSnapshot, String requestedBy) {
        AuditHandler handler = handlerFor(entityType);

        Map<String, Object> before = null;
        if (actionType == AuditActionType.UPDATE || actionType == AuditActionType.DELETE) {
            before = handler.snapshotOf(entityId);
            if (auditRequestMapper.findPendingByEntity(entityType, entityId) != null) {
                throw new DuplicatePendingAuditRequestException(entityType, entityId);
            }
        }
        if (actionType == AuditActionType.CREATE || actionType == AuditActionType.UPDATE) {
            handler.validate(actionType, entityId, afterSnapshot);
        }

        String summary = handler.summarize(afterSnapshot != null ? afterSnapshot : before);

        AuditRequest request = new AuditRequest();
        request.setEntityType(entityType);
        request.setActionType(actionType.name());
        request.setEntityId(entityId);
        request.setBeforeSnapshot(toJson(before));
        request.setAfterSnapshot(toJson(afterSnapshot));
        request.setSummary(summary);
        request.setStatus(PENDING);
        request.setRequestedBy(requestedBy);

        auditRequestMapper.insert(request);
        return auditRequestMapper.findById(request.getId());
    }

    @Transactional
    public AuditRequest approve(Long id, String reviewedBy) {
        AuditRequest request = getById(id);
        ensurePending(request);

        AuditHandler handler = handlerFor(request.getEntityType());
        AuditActionType actionType = AuditActionType.valueOf(request.getActionType());
        Map<String, Object> after = fromJson(request.getAfterSnapshot());

        if (actionType != AuditActionType.DELETE) {
            handler.validate(actionType, request.getEntityId(), after);
        }
        Long resultId = handler.apply(actionType, request.getEntityId(), after);

        if (actionType == AuditActionType.CREATE) {
            request.setEntityId(resultId);
        }
        request.setStatus(APPROVED);
        request.setReviewedBy(reviewedBy);
        request.setReviewedAt(LocalDateTime.now());

        auditRequestMapper.update(request);
        return auditRequestMapper.findById(id);
    }

    @Transactional
    public AuditRequest reject(Long id, String reviewedBy, String rejectReason) {
        AuditRequest request = getById(id);
        ensurePending(request);

        request.setStatus(REJECTED);
        request.setReviewedBy(reviewedBy);
        request.setReviewedAt(LocalDateTime.now());
        request.setRejectReason(rejectReason);

        auditRequestMapper.update(request);
        return auditRequestMapper.findById(id);
    }

    private void ensurePending(AuditRequest request) {
        if (!PENDING.equals(request.getStatus())) {
            throw new AuditRequestAlreadyReviewedException(request.getId(), request.getStatus());
        }
    }

    private AuditHandler handlerFor(String entityType) {
        AuditHandler handler = handlers.get(entityType);
        if (handler == null) {
            // Rows in audit_request are only ever created by this service for a
            // registered entityType, so this indicates a handler was removed or
            // renamed without a corresponding data migration - a server error,
            // not a normal client-facing error path.
            throw new IllegalStateException("No AuditHandler registered for entityType: " + entityType);
        }
        return handler;
    }

    private String toJson(Map<String, Object> snapshot) {
        if (snapshot == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize audit snapshot", e);
        }
    }

    private Map<String, Object> fromJson(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to deserialize audit snapshot", e);
        }
    }
}
