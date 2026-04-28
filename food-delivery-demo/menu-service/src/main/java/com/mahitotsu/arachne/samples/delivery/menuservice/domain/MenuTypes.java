package com.mahitotsu.arachne.samples.delivery.menuservice.domain;

import java.math.BigDecimal;
import java.util.List;

public final class MenuTypes {

    private MenuTypes() {
    }

        public record MenuSuggestionRequest(String sessionId, String query, String refinement, String recentOrderSummary) {

                public MenuSuggestionRequest(String sessionId, String query) {
                        this(sessionId, query, null, null);
                }
    }

    public record MenuSubstitutionRequest(String sessionId, String message, String unavailableItemId) {
    }

    public record MenuSuggestionResponse(String service, String agent, String headline, String summary, List<MenuItem> items,
            int etaMinutes, KitchenTrace kitchenTrace) {
    }

    public record MenuSubstitutionResponse(String service, String agent, String headline, String summary, List<MenuItem> items) {
    }

    public record MenuSuggestionDecision(List<String> selectedItemIds, String skillTag, String recommendationReason) {
    }

    public record MenuSubstitutionDecision(List<String> selectedItemIds, String summary) {
    }

    public record MenuItem(String id, String name, String description, BigDecimal price, int suggestedQuantity, String category,
            List<String> tags) {
    }

    public record KitchenCheckRequest(String sessionId, String message, List<String> itemIds) {
    }

    public record KitchenCheckResponse(String service, String agent, String headline, String summary, int readyInMinutes,
            List<KitchenItemStatus> items, List<AgentCollaboration> collaborations) {
    }

    public record KitchenItemStatus(String itemId, boolean available, int prepMinutes, String substituteItemId, String substituteName,
            BigDecimal substitutePrice) {
    }

    public record AgentCollaboration(String service, String agent, String headline, String summary) {
    }

    public record RegistryServiceDescriptorPayload(String serviceName, String endpoint, String status) {
    }

    public record KitchenTrace(String summary, List<String> notes) {
    }
}