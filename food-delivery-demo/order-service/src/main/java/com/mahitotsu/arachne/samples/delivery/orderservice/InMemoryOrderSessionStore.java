package com.mahitotsu.arachne.samples.delivery.orderservice;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "delivery.order.session-store", havingValue = "in-memory")
class InMemoryOrderSessionStore implements OrderSessionStore {

    private final ConcurrentMap<String, OrderSession> sessions = new ConcurrentHashMap<>();

    @Override
    public Optional<OrderSession> load(String sessionId) {
        return Optional.ofNullable(sessions.get(key(sessionId)));
    }

    @Override
    public void save(OrderSession session) {
        sessions.put(key(session.sessionId()), session);
    }

    private String key(String sessionId) {
        return SecurityAccessors.currentCustomerId() + ":" + sessionId;
    }
}