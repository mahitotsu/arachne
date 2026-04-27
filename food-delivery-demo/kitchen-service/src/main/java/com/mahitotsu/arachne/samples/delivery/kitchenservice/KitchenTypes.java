package com.mahitotsu.arachne.samples.delivery.kitchenservice;

import java.math.BigDecimal;
import java.util.List;

record KitchenCheckRequest(String sessionId, String message, List<String> itemIds) {
}

record KitchenCheckResponse(
        String service,
        String agent,
        String headline,
        String summary,
        int readyInMinutes,
        List<KitchenItemStatus> items,
        List<AgentCollaboration> collaborations) {
}

record KitchenItemStatus(String itemId, boolean available, int prepMinutes, String substituteItemId, String substituteName, BigDecimal substitutePrice) {
}

record KitchenStockState(boolean available, int prepMinutes, String lineType) {
}

record PrepSchedule(int readyInMinutes, String bottleneckLine, String summary, String alternativeSuggestion) {
}

record AgentCollaboration(String service, String agent, String headline, String summary) {
}

record MenuSubstitutionRequest(String sessionId, String message, String unavailableItemId) {
}

record MenuSubstitutionResponse(String service, String agent, String headline, String summary, List<MenuItem> items) {
}

record RegistryServiceDescriptorPayload(
        String serviceName,
        String endpoint,
        String capability,
        String agentName,
        String requestMethod,
        String requestPath,
        String status) {
}

record MenuItem(String id, String name, String description, BigDecimal price, int suggestedQuantity,
        String category, List<String> tags) {
}