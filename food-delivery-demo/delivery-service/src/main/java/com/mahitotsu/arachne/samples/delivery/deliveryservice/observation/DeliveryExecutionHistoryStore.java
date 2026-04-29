package com.mahitotsu.arachne.samples.delivery.deliveryservice.observation;

import static com.mahitotsu.arachne.samples.delivery.deliveryservice.domain.DeliveryExecutionHistoryTypes.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

@Component
public class DeliveryExecutionHistoryStore {

    private static final int MAX_EVENTS_PER_SESSION = 256;

    private final AtomicLong sequence = new AtomicLong();
    private final Map<String, ConcurrentLinkedDeque<DeliveryExecutionHistoryEvent>> eventsBySession = new ConcurrentHashMap<>();

    public void append(
            String sessionId,
            String category,
            String service,
            String component,
            String operation,
            String outcome,
            long durationMs,
            String headline,
            String detail,
            AgentUsageBreakdown usage,
            List<String> skills) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        ConcurrentLinkedDeque<DeliveryExecutionHistoryEvent> events = eventsBySession.computeIfAbsent(
                sessionId,
                ignored -> new ConcurrentLinkedDeque<>());
        events.addLast(new DeliveryExecutionHistoryEvent(
                sequence.incrementAndGet(),
                Instant.now().toString(),
                category,
                service,
                component,
                operation,
                outcome,
                durationMs,
                headline,
                detail,
                usage,
                skills == null ? List.of() : List.copyOf(skills)));
        while (events.size() > MAX_EVENTS_PER_SESSION) {
            events.pollFirst();
        }
    }

    public void append(
            String sessionId,
            String category,
            String service,
            String component,
            String operation,
            String outcome,
            long durationMs,
            String headline,
            String detail) {
        append(sessionId, category, service, component, operation, outcome, durationMs, headline, detail, null, List.of());
    }

    public DeliveryExecutionHistoryResponse history(String sessionId) {
        List<DeliveryExecutionHistoryEvent> events = new ArrayList<>(eventsBySession.getOrDefault(
                sessionId,
                new ConcurrentLinkedDeque<>()));
        events.sort(Comparator.comparingLong(DeliveryExecutionHistoryEvent::sequence));
        return new DeliveryExecutionHistoryResponse(sessionId, List.copyOf(events));
    }
}