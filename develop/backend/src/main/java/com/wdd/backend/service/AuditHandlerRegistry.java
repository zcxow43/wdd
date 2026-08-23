package com.wdd.backend.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Resolves the {@link AuditHandler} registered for an {@code entityType}.
 * Spring injects every {@code AuditHandler} bean on the classpath — adding a
 * new audited entity means adding a new {@code @Component} implementing the
 * interface elsewhere, never touching this class.
 */
@Component
public class AuditHandlerRegistry {

    private final Map<String, AuditHandler> handlersByEntityType;

    public AuditHandlerRegistry(List<AuditHandler> handlers) {
        Map<String, AuditHandler> map = new HashMap<>();
        for (AuditHandler handler : handlers) {
            map.put(handler.entityType(), handler);
        }
        this.handlersByEntityType = Map.copyOf(map);
    }

    /**
     * @throws IllegalStateException if no handler is registered for
     *                                {@code entityType} — an unknown entity
     *                                type is a server-side wiring error, not
     *                                a client error, so this is deliberately
     *                                left uncaught to surface as a {@code 500}.
     */
    public AuditHandler resolve(String entityType) {
        AuditHandler handler = handlersByEntityType.get(entityType);
        if (handler == null) {
            throw new IllegalStateException("No audit handler registered for entityType: " + entityType);
        }
        return handler;
    }
}
