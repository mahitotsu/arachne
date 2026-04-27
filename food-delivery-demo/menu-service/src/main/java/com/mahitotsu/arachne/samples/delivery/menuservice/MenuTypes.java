package com.mahitotsu.arachne.samples.delivery.menuservice;

import java.math.BigDecimal;
import java.util.List;

record MenuSuggestionRequest(String sessionId, String message) {
}

record MenuSubstitutionRequest(String sessionId, String message, String unavailableItemId) {
}

record MenuSuggestionResponse(String service, String agent, String headline, String summary, List<MenuItem> items,
        int etaMinutes, KitchenTrace kitchenTrace) {
}

record MenuSubstitutionResponse(String service, String agent, String headline, String summary, List<MenuItem> items) {
}

record MenuItem(String id, String name, String description, BigDecimal price, int suggestedQuantity, String category,
        List<String> tags) {
}

record KitchenCheckRequest(String sessionId, String message, List<String> itemIds) {
}

record KitchenCheckResponse(String service, String agent, String headline, String summary, int readyInMinutes,
        List<KitchenItemStatus> items, List<AgentCollaboration> collaborations) {
}

record KitchenItemStatus(String itemId, boolean available, int prepMinutes, String substituteItemId, String substituteName,
        BigDecimal substitutePrice) {
}

record AgentCollaboration(String service, String agent, String headline, String summary) {
}

record RegistryServiceDescriptorPayload(String serviceName, String endpoint, String status) {
}

record KitchenTrace(String summary, List<String> notes) {
}