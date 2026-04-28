package com.mahitotsu.arachne.samples.delivery.orderservice.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.DeliveryOptionChoice;
import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.OrderIntentInput;
import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.OrderDraft;
import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.OrderSession;
import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.PendingDeliverySelection;
import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.PendingProposal;
import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.StoredOrder;
import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.SuggestOrderRequest;

class MenuSuggestionPromptRequestFactoryTest {

    @Test
    void buildsExplicitMenuPromptFieldsForRepeatOrderAndRefinement() {
        SuggestOrderRequest request = new SuggestOrderRequest("session-1", "いつものやつで", "ja-JP", "辛さ控えめ");

        var prompt = MenuSuggestionPromptRequestFactory.build(
                "session-1",
                request,
                emptySession(),
                Optional.of(new StoredOrder("ord-1", "2x Teriyaki Chicken Box", BigDecimal.TEN, BigDecimal.TEN, "18 min", "CHARGED")));

        assertThat(prompt.query()).isEqualTo("いつものやつで");
        assertThat(prompt.refinement()).isEqualTo("辛さ控えめ");
        assertThat(prompt.recentOrderSummary()).isEqualTo("2x Teriyaki Chicken Box");
    }

    @Test
    void usesPendingProposalMessageWhenFreshMessageIsBlank() {
        SuggestOrderRequest request = new SuggestOrderRequest("session-1", "   ", "ja-JP", null);

        var prompt = MenuSuggestionPromptRequestFactory.build(
                "session-1",
                request,
                sessionWithPendingProposal("前回と同じ感じで"),
                Optional.empty());

        assertThat(prompt.query()).isEqualTo("前回と同じ感じで");
        assertThat(prompt.recentOrderSummary()).isEqualTo("none");
    }

    @Test
    void buildsQueryFromStructuredIntentWhenRawMessageIsMissing() {
        SuggestOrderRequest request = new SuggestOrderRequest(
                "session-1",
                new OrderIntentInput(null, 4, new BigDecimal("4000"), 1),
                "ja-JP",
                null);

        var prompt = MenuSuggestionPromptRequestFactory.build(
                "session-1",
                request,
                emptySession(),
                Optional.empty());

        assertThat(prompt.query()).contains("4人", "4000円以内", "子ども1人");
    }

    private static OrderSession emptySession() {
        return new OrderSession(
                "session-1",
                "initial",
                new OrderDraft("INITIAL", List.of(), BigDecimal.ZERO, BigDecimal.ZERO, "", "PENDING", "", ""),
                null,
                null,
                null);
    }

    private static OrderSession sessionWithPendingProposal(String customerMessage) {
        return new OrderSession(
                "session-1",
                "item-selection",
                new OrderDraft("PROPOSAL_READY", List.of(), BigDecimal.ZERO, BigDecimal.ZERO, "", "PENDING", "", ""),
                new PendingProposal(customerMessage, "ja-JP", "summary", List.of(), 10, null),
                new PendingDeliverySelection("", List.<DeliveryOptionChoice>of()),
                null);
    }
}