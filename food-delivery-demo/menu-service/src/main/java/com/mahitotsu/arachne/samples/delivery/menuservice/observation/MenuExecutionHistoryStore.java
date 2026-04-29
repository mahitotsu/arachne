package com.mahitotsu.arachne.samples.delivery.menuservice.observation;

import static com.mahitotsu.arachne.samples.delivery.menuservice.domain.MenuExecutionHistoryTypes.*;

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
public class MenuExecutionHistoryStore {

    private static final int MAX_EVENTS_PER_SESSION = 256;

    private final AtomicLong sequence = new AtomicLong();
    private final Map<String, ConcurrentLinkedDeque<MenuExecutionHistoryEvent>> eventsBySession = new ConcurrentHashMap<>();

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
        ConcurrentLinkedDeque<MenuExecutionHistoryEvent> events = eventsBySession.computeIfAbsent(
                sessionId,
                ignored -> new ConcurrentLinkedDeque<>());
        events.addLast(new MenuExecutionHistoryEvent(
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

    public MenuExecutionHistoryResponse history(String sessionId) {
        List<MenuExecutionHistoryEvent> events = new ArrayList<>(eventsBySession.getOrDefault(
                sessionId,
                new ConcurrentLinkedDeque<>()));
        events.sort(Comparator.comparingLong(MenuExecutionHistoryEvent::sequence));
        return new MenuExecutionHistoryResponse(sessionId, List.copyOf(events));
    }
}