package com.mahitotsu.arachne.samples.delivery.kitchenservice.domain;

import java.math.BigDecimal;
import java.util.List;

public final class KitchenTypes {

    private KitchenTypes() {
    }

    public record KitchenCheckRequest(String sessionId, String message, List<String> itemIds) {
    }

    public record KitchenCheckResponse(
            String service,
            String agent,
            String headline,
            String summary,
            int readyInMinutes,
            List<KitchenItemStatus> items,
            List<AgentCollaboration> collaborations) {
    }

    public record KitchenItemStatus(String itemId, boolean available, int prepMinutes, String substituteItemId, String substituteName, BigDecimal substitutePrice) {
    }

    public record KitchenStockState(boolean available, int prepMinutes, String lineType) {
    }

    public record PrepSchedule(int readyInMinutes, String bottleneckLine, String summary, String alternativeSuggestion) {
    }

    public record AgentCollaboration(String service, String agent, String headline, String summary) {
    }

    public record MenuSubstitutionRequest(String sessionId, String message, String unavailableItemId) {
    }

    public record MenuSubstitutionResponse(String service, String agent, String headline, String summary, List<MenuItem> items) {
    }

    public record RegistryServiceDescriptorPayload(
            String serviceName,
            String endpoint,
            String capability,
            String agentName,
            String requestMethod,
            String requestPath,
            String status) {
    }

    public record MenuItem(String id, String name, String description, BigDecimal price, int suggestedQuantity,
            String category, List<String> tags) {
    }
}