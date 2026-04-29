package com.mahitotsu.arachne.samples.delivery.orderservice.infrastructure;

import static com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderExecutionHistoryTypes.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

@Component
public class OrderExecutionHistoryStore {

    private static final int MAX_EVENTS_PER_SESSION = 256;

    private final AtomicLong sequence = new AtomicLong();
    private final Map<String, ConcurrentLinkedDeque<OrderExecutionHistoryEntry>> eventsBySession = new ConcurrentHashMap<>();

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
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        ConcurrentLinkedDeque<OrderExecutionHistoryEntry> events = eventsBySession.computeIfAbsent(
                sessionId,
                ignored -> new ConcurrentLinkedDeque<>());
        events.addLast(new OrderExecutionHistoryEntry(
                sequence.incrementAndGet(),
            java.time.Instant.now().toString(),
                category,
                service,
                component,
                operation,
                outcome,
                durationMs,
                headline,
                detail));
        while (events.size() > MAX_EVENTS_PER_SESSION) {
            events.pollFirst();
        }
    }

    public OrderExecutionHistoryResponse history(String sessionId) {
        List<OrderExecutionHistoryEntry> events = new ArrayList<>(eventsBySession.getOrDefault(
                sessionId,
                new ConcurrentLinkedDeque<>()));
        events.sort(Comparator.comparingLong(OrderExecutionHistoryEntry::sequence));
        return new OrderExecutionHistoryResponse(sessionId, List.copyOf(events));
    }
}