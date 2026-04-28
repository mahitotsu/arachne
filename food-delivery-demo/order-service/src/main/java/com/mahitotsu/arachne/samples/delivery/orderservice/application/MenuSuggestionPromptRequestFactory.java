package com.mahitotsu.arachne.samples.delivery.orderservice.application;

import java.util.Locale;
import java.util.Optional;

import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.MenuSuggestionRequest;
import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.OrderSession;
import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.StoredOrder;
import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.SuggestOrderRequest;

final class MenuSuggestionPromptRequestFactory {

    private MenuSuggestionPromptRequestFactory() {
    }

    static MenuSuggestionRequest build(String sessionId, SuggestOrderRequest request, OrderSession existing, Optional<StoredOrder> recentOrder) {
        String query = firstNonBlank(
                request.message(),
                existing.pendingProposal() == null ? null : existing.pendingProposal().customerMessage());
        String refinement = request.refinement() == null || request.refinement().isBlank()
                ? null
                : request.refinement().trim();
        String recentOrderSummary = needsRecentOrderContext(query)
                ? recentOrder.map(StoredOrder::itemSummary).orElse("none")
                : null;
        return new MenuSuggestionRequest(sessionId, query, refinement, recentOrderSummary);
    }

    private static boolean needsRecentOrderContext(String message) {
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        return normalized.contains("前回") || normalized.contains("いつもの") || normalized.contains("same as last time");
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }
        return "";
    }
}