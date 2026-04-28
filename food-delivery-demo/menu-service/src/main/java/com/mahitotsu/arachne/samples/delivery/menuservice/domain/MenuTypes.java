package com.mahitotsu.arachne.samples.delivery.menuservice.domain;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

public final class MenuTypes {

    private MenuTypes() {
    }

    @Schema(description = "Menu suggestion request sent to menu-service.")
        public record MenuSuggestionRequest(
                @Schema(description = "Correlation identifier for the calling workflow.") String sessionId,
                @Schema(description = "Primary customer order intent for this turn.", example = "2人で子ども向けのセットを見せて") String query,
                @Schema(description = "Optional additional constraints for a follow-up suggestion.") String refinement,
                @Schema(description = "Optional recent-order summary used to support repeat-order suggestions.") String recentOrderSummary) {

                public MenuSuggestionRequest(String sessionId, String query) {
                        this(sessionId, query, null, null);
                }
    }

    @Schema(description = "Fallback candidate request for an unavailable menu item.")
    public record MenuSubstitutionRequest(
            @Schema(description = "Correlation identifier for the calling workflow.") String sessionId,
            @Schema(description = "Original customer intent to preserve when searching substitutes.") String message,
            @Schema(description = "Unavailable item id that triggered the substitution flow.") String unavailableItemId) {
    }

    @Schema(description = "Menu suggestion response with agent summary and catalog-backed items.")
    public record MenuSuggestionResponse(String service, String agent, String headline, String summary, List<MenuItem> items,
            int etaMinutes, KitchenTrace kitchenTrace) {
    }

    @Schema(description = "Menu substitution response containing fallback candidates.")
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