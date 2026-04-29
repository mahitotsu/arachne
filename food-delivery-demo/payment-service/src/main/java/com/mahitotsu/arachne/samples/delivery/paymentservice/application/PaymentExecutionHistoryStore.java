package com.mahitotsu.arachne.samples.delivery.paymentservice.application;

import static com.mahitotsu.arachne.samples.delivery.paymentservice.domain.PaymentExecutionHistoryTypes.*;

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
public class PaymentExecutionHistoryStore {

    private static final int MAX_EVENTS_PER_SESSION = 128;

    private final AtomicLong sequence = new AtomicLong();
    private final Map<String, ConcurrentLinkedDeque<PaymentExecutionHistoryEvent>> eventsBySession = new ConcurrentHashMap<>();

    public void append(String sessionId, String operation, String outcome, long durationMs, String headline, String detail) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        ConcurrentLinkedDeque<PaymentExecutionHistoryEvent> events = eventsBySession.computeIfAbsent(
                sessionId,
                ignored -> new ConcurrentLinkedDeque<>());
        events.addLast(new PaymentExecutionHistoryEvent(
                sequence.incrementAndGet(),
                Instant.now().toString(),
                "service",
                "payment-service",
                "payment-service",
                operation,
                outcome,
                durationMs,
                headline,
                detail));
        while (events.size() > MAX_EVENTS_PER_SESSION) {
            events.pollFirst();
        }
    }

    public PaymentExecutionHistoryResponse history(String sessionId) {
        List<PaymentExecutionHistoryEvent> events = new ArrayList<>(eventsBySession.getOrDefault(
                sessionId,
                new ConcurrentLinkedDeque<>()));
        events.sort(Comparator.comparingLong(PaymentExecutionHistoryEvent::sequence));
        return new PaymentExecutionHistoryResponse(sessionId, List.copyOf(events));
    }
}