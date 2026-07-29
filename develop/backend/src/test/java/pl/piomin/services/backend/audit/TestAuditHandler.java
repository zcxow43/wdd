package pl.piomin.services.backend.audit;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Test-only {@link AuditHandler} that exists purely to prove the generic
 * audit module works end-to-end without any real domain consumer (e.g.
 * {@code CurrencyPairAuditHandler}) wired in. It lives on the test classpath
 * only, under the component-scanned {@code pl.piomin.services.backend}
 * package, so Spring registers it as a real bean for
 * {@code @SpringBootTest} runs but it never ships in production.
 */
@Component
public class TestAuditHandler implements AuditHandler {

    public static final String ENTITY_TYPE = "TEST_ENTITY";

    private final Map<Long, Map<String, Object>> store = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong(1);
    private volatile boolean rejectNextValidation = false;

    @Override
    public String entityType() {
        return ENTITY_TYPE;
    }

    @Override
    public Map<String, Object> snapshotOf(Long entityId) {
        Map<String, Object> snapshot = store.get(entityId);
        if (snapshot == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Test entity not found: " + entityId);
        }
        return new HashMap<>(snapshot);
    }

    @Override
    public void validate(AuditActionType actionType, Long entityId, Map<String, Object> afterSnapshot) {
        if (rejectNextValidation) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Forced validation failure");
        }
        if (afterSnapshot == null || afterSnapshot.get("name") == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
    }

    @Override
    public Long apply(AuditActionType actionType, Long entityId, Map<String, Object> afterSnapshot) {
        switch (actionType) {
            case CREATE -> {
                long id = idSequence.getAndIncrement();
                store.put(id, new HashMap<>(afterSnapshot));
                return id;
            }
            case UPDATE -> {
                if (!store.containsKey(entityId)) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Test entity not found: " + entityId);
                }
                store.put(entityId, new HashMap<>(afterSnapshot));
                return entityId;
            }
            case DELETE -> {
                if (store.remove(entityId) == null) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Test entity not found: " + entityId);
                }
                return entityId;
            }
            default -> throw new IllegalArgumentException("Unsupported action: " + actionType);
        }
    }

    @Override
    public String summarize(Map<String, Object> snapshot) {
        return snapshot != null ? "TEST · " + snapshot.get("name") : "TEST";
    }

    // Test-support helpers below - not part of the AuditHandler contract.

    public Long seed(Map<String, Object> fields) {
        long id = idSequence.getAndIncrement();
        store.put(id, new HashMap<>(fields));
        return id;
    }

    public boolean exists(Long id) {
        return store.containsKey(id);
    }

    public void setRejectNextValidation(boolean reject) {
        this.rejectNextValidation = reject;
    }

    public void reset() {
        store.clear();
        idSequence.set(1);
        rejectNextValidation = false;
    }
}
