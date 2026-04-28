package com.mahitotsu.arachne.samples.delivery.menuservice.domain;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

public final class MenuTypes {

    private MenuTypes() {
    }

        @Schema(description = "menu-service へ送る menu 提案要求です。")
        public record MenuSuggestionRequest(
                                @Schema(description = "呼び出し元ワークフローの相関 ID。") String sessionId,
                                @Schema(description = "このターンにおける主たる customer の注文意図。", example = "2人で子ども向けのセットを見せて") String query,
                                @Schema(description = "追加入力による再提案のための任意制約。") String refinement,
                                @Schema(description = "再注文提案を補助するための任意の最近の注文要約。") String recentOrderSummary) {

                public MenuSuggestionRequest(String sessionId, String query) {
                        this(sessionId, query, null, null);
                }
    }

    @Schema(description = "欠品 menu item に対する代替候補要求です。")
    public record MenuSubstitutionRequest(
            @Schema(description = "呼び出し元ワークフローの相関 ID。") String sessionId,
            @Schema(description = "代替候補探索時に維持すべき元の customer 意図。") String message,
            @Schema(description = "代替フローの起点となった欠品 item id。") String unavailableItemId) {
    }

    @Schema(description = "agent 要約と catalog ベースの商品を含む menu 提案応答です。")
    public record MenuSuggestionResponse(String service, String agent, String headline, String summary, List<MenuItem> items,
            int etaMinutes, KitchenTrace kitchenTrace) {
    }

    @Schema(description = "代替候補を含む menu 代替応答です。")
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