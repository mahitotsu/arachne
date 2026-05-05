package com.mahitotsu.arachne.samples.delivery.orderservice.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.DeliveryOptionChoice;
import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.MenuGroundingContext;
import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.NormalizedOrderIntent;
import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.OrderIntentInput;
import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.OrderDraft;
import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.OrderSession;
import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.PendingDeliverySelection;
import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.PendingProposal;
import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.SuggestOrderRequest;

class MenuSuggestionPromptRequestFactoryTest {

    @Test
    void buildsGroundingRequestFromNormalizedIntentAndRefinement() {
        SuggestOrderRequest request = new SuggestOrderRequest("session-1", "いつものやつで", "ja-JP", "辛さ控えめ");
    NormalizedOrderIntent normalizedIntent = new NormalizedOrderIntent(
        "いつものやつで",
        "REORDER",
        "いつものやつで",
        null,
        null,
        null,
        null,
        "2x Teriyaki Chicken Box",
        "再注文の文脈があるため前回注文を参照する形に正規化しました。");

        var prompt = MenuSuggestionPromptRequestFactory.build(
                "session-1",
                request,
        normalizedIntent);

        assertThat(prompt.query()).isEqualTo("いつものやつで");
        assertThat(prompt.refinement()).isEqualTo("辛さ控えめ");
    assertThat(prompt.recentOrderSummary()).isEqualTo("2x Teriyaki Chicken Box");
    assertThat(prompt.groundingContext()).isEqualTo(new MenuGroundingContext(
        "REORDER",
        null,
        null,
        null,
        null,
        "再注文の文脈があるため前回注文を参照する形に正規化しました。"));
    }

    @Test
    void resolvesCustomerMessageFromPendingProposalWhenFreshMessageIsBlank() {
    SuggestOrderRequest request = new SuggestOrderRequest("session-1", "   ", "ja-JP", null);

    assertThat(MenuSuggestionPromptRequestFactory.resolveCustomerMessage(
        request,
        sessionWithPendingProposal("前回と同じ感じで"))).isEqualTo("前回と同じ感じで");
    }

    @Test
    void exposesStructuredConstraintsInGroundingContext() {
    SuggestOrderRequest request = new SuggestOrderRequest(
                "session-1",
                new OrderIntentInput(null, 4, new BigDecimal("4000"), 1),
                "ja-JP",
                null);
    NormalizedOrderIntent normalizedIntent = new NormalizedOrderIntent(
        "4人、4000円以内、子ども1人",
        "RECOMMENDATION",
        "4人、4000円以内、子ども1人",
        null,
        4,
        new BigDecimal("4000"),
        1,
        null,
        "人数や予算などの条件から recommendation planning として扱います。");

        var prompt = MenuSuggestionPromptRequestFactory.build(
                "session-1",
                request,
        normalizedIntent);

    assertThat(prompt.query()).contains("4人", "4000円以内", "子ども1人");
    assertThat(prompt.groundingContext()).isEqualTo(new MenuGroundingContext(
        "RECOMMENDATION",
        null,
        4,
        new BigDecimal("4000"),
        1,
        "人数や予算などの条件から recommendation planning として扱います。"));
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