package com.mahitotsu.arachne.samples.delivery.orderservice.application;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;

import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.MenuSuggestionRequest;
import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.OrderIntentInput;
import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.OrderSession;
import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.StoredOrder;
import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.SuggestOrderRequest;

final class MenuSuggestionPromptRequestFactory {

    private MenuSuggestionPromptRequestFactory() {
    }

    static MenuSuggestionRequest build(String sessionId, SuggestOrderRequest request, OrderSession existing, Optional<StoredOrder> recentOrder) {
        String query = resolveQuery(request, existing);
        String refinement = request.refinement() == null || request.refinement().isBlank()
                ? null
                : request.refinement().trim();
        String recentOrderSummary = needsRecentOrderContext(query)
                ? recentOrder.map(StoredOrder::itemSummary).orElse("none")
                : null;
        return new MenuSuggestionRequest(sessionId, query, refinement, recentOrderSummary);
    }

        static String resolveQuery(SuggestOrderRequest request, OrderSession existing) {
        return firstNonBlank(
            rawMessage(request.intent()),
            structuredIntentSummary(request.intent()),
            existing.pendingProposal() == null ? null : existing.pendingProposal().customerMessage());
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

    private static String firstNonBlank(String first, String second, String third) {
        return firstNonBlank(firstNonBlank(first, second), third);
    }

    private static String rawMessage(OrderIntentInput intent) {
        return intent == null ? null : intent.rawMessage();
    }

    private static String structuredIntentSummary(OrderIntentInput intent) {
        if (intent == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        appendClause(builder, intent.partySize() == null ? null : intent.partySize() + "人");
        appendClause(builder, formatBudget(intent.budgetUpperBound()));
        appendClause(builder, intent.childCount() == null ? null : "子ども" + intent.childCount() + "人");
        return builder.toString();
    }

    private static void appendClause(StringBuilder builder, String clause) {
        if (clause == null || clause.isBlank()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append('、');
        }
        builder.append(clause);
    }

    private static String formatBudget(BigDecimal budgetUpperBound) {
        if (budgetUpperBound == null) {
            return null;
        }
        return budgetUpperBound.stripTrailingZeros().toPlainString() + "円以内";
    }
}