package com.mahitotsu.arachne.samples.delivery.supportservice.domain;

import java.util.List;

public final class SupportExecutionHistoryTypes {

    private SupportExecutionHistoryTypes() {
    }

    public record SupportExecutionHistoryResponse(String sessionId, List<SupportExecutionHistoryEvent> events) {
    }

    public record SupportExecutionHistoryEvent(
            long sequence,
            String occurredAt,
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
    }

    public record AgentUsageBreakdown(int inputTokens, int outputTokens, int cacheReadTokens, int cacheWriteTokens) {
    }
}