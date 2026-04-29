package com.mahitotsu.arachne.samples.delivery.paymentservice.domain;

import java.util.List;

public final class PaymentExecutionHistoryTypes {

    private PaymentExecutionHistoryTypes() {
    }

    public record PaymentExecutionHistoryResponse(String sessionId, List<PaymentExecutionHistoryEvent> events) {
    }

    public record PaymentExecutionHistoryEvent(
            long sequence,
            String occurredAt,
            String category,
            String service,
            String component,
            String operation,
            String outcome,
            long durationMs,
            String headline,
            String detail) {
    }
}