package com.mahitotsu.arachne.samples.delivery.kitchenservice.domain;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

public final class KitchenTypes {

    private KitchenTypes() {
    }

    @Schema(description = "kitchen 在庫確認要求です。")
    public record KitchenCheckRequest(
            @Schema(description = "親注文ワークフローの相関 ID。") String sessionId,
            @Schema(description = "代替判定時にも保持すべき元の customer 意図。") String message,
            @Schema(description = "検証対象として選択された menu item id。") List<String> itemIds) {
    }

    @Schema(description = "検証済み商品と協調 trace を含む kitchen 在庫確認応答です。")
    public record KitchenCheckResponse(
            String service,
            String agent,
            String headline,
            String summary,
            int readyInMinutes,
            List<KitchenItemStatus> items,
            List<AgentCollaboration> collaborations) {
    }

    public record KitchenDecision(String summary, List<ApprovedSubstitutionDecision> approvedSubstitutions) {
    }

    public record ApprovedSubstitutionDecision(String unavailableItemId, String selectedItemId) {
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