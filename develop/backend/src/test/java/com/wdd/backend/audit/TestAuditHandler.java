package com.wdd.backend.audit;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Test-only fake {@link AuditHandler}, registered as a real Spring bean purely so
 * {@code AuditControllerTest} can prove the generic audit module works end-to-end without
 * any real consumer (e.g. {@code CurrencyPairAuditHandler}) wired in. Lives only on the test
 * classpath (under {@code src/test/java}) so it never ships in the production jar, even
 * though it sits in the same {@code com.wdd.backend.audit} package scanned by
 * {@code @SpringBootApplication}.
 *
 * <p>Backs a tiny in-memory "entity table" (keyed by id) so {@code snapshotOf}/{@code apply}
 * behave like a real handler would against a real table.
 */
@Component
public class TestAuditHandler implements AuditHandler {

    public static final String ENTITY_TYPE = "TEST_ENTITY";

    private final Map<Long, Map<String, Object>> entities = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong(1000);

    /** Test hook: wipe all in-memory state between tests. */
    public void reset() {
        entities.clear();
        idSequence.set(1000);
    }

    /** Test hook: seed a "live" entity so snapshotOf/validate/apply have something to work with. */
    public Long seed(Map<String, Object> fields) {
        Long id = idSequence.incrementAndGet();
        entities.put(id, new LinkedHashMap<>(fields));
        return id;
    }

    public boolean exists(Long id) {
        return entities.containsKey(id);
    }

    public Map<String, Object> get(Long id) {
        return entities.get(id);
    }

    @Override
    public String entityType() {
        return ENTITY_TYPE;
    }

    @Override
    public Map<String, Object> snapshotOf(Long entityId) {
        Map<String, Object> entity = entities.get(entityId);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Test entity not found: " + entityId);
        }
        return new LinkedHashMap<>(entity);
    }

    @Override
    public void validate(AuditActionType actionType, Long entityId, Map<String, Object> afterSnapshot) {
        if (afterSnapshot == null) {
            return;
        }
        if (Boolean.TRUE.equals(afterSnapshot.get("forceValidationError"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "forced validation error");
        }
        if (actionType == AuditActionType.CREATE) {
            Object name = afterSnapshot.get("name");
            boolean duplicate = entities.values().stream()
                    .anyMatch(existing -> existing.get("name") != null && existing.get("name").equals(name));
            if (duplicate) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "duplicate name: " + name);
            }
        }
    }

    @Override
    public Long apply(AuditActionType actionType, Long entityId, Map<String, Object> afterSnapshot) {
        switch (actionType) {
            case CREATE: {
                Long newId = idSequence.incrementAndGet();
                entities.put(newId, new LinkedHashMap<>(afterSnapshot));
                return newId;
            }
            case UPDATE: {
                if (!entities.containsKey(entityId)) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Test entity not found: " + entityId);
                }
                entities.put(entityId, new LinkedHashMap<>(afterSnapshot));
                return entityId;
            }
            case DELETE: {
                if (!entities.containsKey(entityId)) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Test entity not found: " + entityId);
                }
                entities.remove(entityId);
                return entityId;
            }
            default:
                throw new IllegalArgumentException("Unknown actionType: " + actionType);
        }
    }

    @Override
    public String summarize(Map<String, Object> snapshot) {
        if (snapshot == null) {
            return "TEST_ENTITY";
        }
        Object name = snapshot.get("name");
        return "TEST_ENTITY · " + name;
    }
}
