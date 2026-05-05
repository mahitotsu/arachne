package com.mahitotsu.arachne.samples.delivery.orderservice.application;

import java.math.BigDecimal;
import java.util.Optional;

import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.OrderIntentInput;
import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.OrderSession;
import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.StoredOrder;
import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.SuggestOrderRequest;

record OrderIntentAgentUserPrompt(
        String rawMessage,
        Integer partySize,
        BigDecimal budgetUpperBound,
        Integer childCount,
        String refinement,
        String previousProposalMessage,
        String recentOrderSummary) {

    static OrderIntentAgentUserPrompt from(
            SuggestOrderRequest request,
            OrderSession existing,
            Optional<StoredOrder> recentOrder) {
        OrderIntentInput intent = request.intent();
        return new OrderIntentAgentUserPrompt(
                intent == null ? null : intent.rawMessage(),
                intent == null ? null : intent.partySize(),
                intent == null ? null : intent.budgetUpperBound(),
                intent == null ? null : intent.childCount(),
                request.refinement(),
                existing.pendingProposal() == null ? null : existing.pendingProposal().customerMessage(),
                recentOrder.map(StoredOrder::itemSummary).orElse(null));
    }

    String render() {
        StringBuilder builder = new StringBuilder();
        append(builder, "raw_message", rawMessage);
        append(builder, "party_size", partySize);
        append(builder, "budget_upper_bound", budgetUpperBound);
        append(builder, "child_count", childCount);
        append(builder, "refinement", refinement);
        append(builder, "previous_proposal_message", previousProposalMessage);
        append(builder, "recent_order", recentOrderSummary);
        return builder.toString().trim();
    }

    private void append(StringBuilder builder, String key, Object value) {
        if (value == null) {
            return;
        }
        String rendered = String.valueOf(value).trim();
        if (rendered.isBlank()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append('\n');
        }
        builder.append(key).append('=').append(rendered);
    }
}