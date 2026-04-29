package com.mahitotsu.arachne.samples.delivery.deliveryservice.domain;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

public final class DeliveryExecutionHistoryTypes {

    private DeliveryExecutionHistoryTypes() {
    }

    @Schema(description = "delivery-service の session 単位実行履歴です。")
    public record DeliveryExecutionHistoryResponse(String sessionId, List<DeliveryExecutionHistoryEvent> events) {
    }

    @Schema(description = "delivery-service の単一実行イベントです。")
    public record DeliveryExecutionHistoryEvent(
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

    @Schema(description = "agent usage の内訳です。")
    public record AgentUsageBreakdown(int inputTokens, int outputTokens, int cacheReadTokens, int cacheWriteTokens) {
    }
}