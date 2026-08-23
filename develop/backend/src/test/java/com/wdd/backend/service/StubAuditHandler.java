package com.wdd.backend.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.wdd.backend.dto.AuditRequest;
import com.wdd.backend.exception.AuditHandlerException;

/**
 * Test-only {@link AuditHandler} used to exercise the approve pipeline
 * (validate -&gt; apply -&gt; status update) end to end without any real
 * audited entity existing yet — the two real entities (currency-pair,
 * spread) register their own handlers in their own specs. Lives entirely
 * under {@code src/test}, so it is never packaged into the running
 * application; it registers itself as a Spring {@code @Component} only
 * because it happens to be on the test classpath when
 * {@code @SpringBootTest} boots the context.
 *
 * <p>{@code entityType()} is {@value #ENTITY_TYPE} — not a real entity name
 * — and it "applies" changes onto an in-memory map ({@link #TARGET_STATE})
 * rather than any table, standing in for "the target row actually
 * changes" from the acceptance criteria.
 */
@Component
public class StubAuditHandler implements AuditHandler {

    public static final String ENTITY_TYPE = "TEST_STUB";

    /** Fake "target" apply() writes to, and tests assert against. */
    public static final Map<Long, Object> TARGET_STATE = new ConcurrentHashMap<>();

    @Override
    public String entityType() {
        return ENTITY_TYPE;
    }

    @Override
    public void validate(AuditRequest request) {
        if (request.getAfterData() instanceof Map<?, ?> map && Boolean.TRUE.equals(map.get("forceFail"))) {
            throw new AuditHandlerException("stub target drifted: forced failure for test");
        }
    }

    @Override
    public void apply(AuditRequest request) {
        if (request.getEntityId() != null) {
            TARGET_STATE.put(request.getEntityId(), request.getAfterData());
        }
    }
}
