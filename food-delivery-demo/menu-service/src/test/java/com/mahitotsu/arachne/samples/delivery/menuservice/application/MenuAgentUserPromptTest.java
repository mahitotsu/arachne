package com.mahitotsu.arachne.samples.delivery.menuservice.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.mahitotsu.arachne.samples.delivery.menuservice.domain.MenuTypes.MenuGroundingContext;
import com.mahitotsu.arachne.samples.delivery.menuservice.domain.MenuTypes.MenuSuggestionRequest;

class MenuAgentUserPromptTest {

    @Test
    void rendersNormalizedGroundingFieldsAsNamedPromptSections() {
        String rendered = MenuAgentUserPrompt.from(new MenuSuggestionRequest(
                "session-1",
                "家族向けのおすすめ",
        null,
        "Teriyaki Chicken Box",
        new MenuGroundingContext(
            "RECOMMENDATION",
            null,
            4,
            new java.math.BigDecimal("4000"),
            1,
            "人数と予算に合う構成を優先しました。")))
        .render();

    assertThat(rendered).isEqualTo("""
        query=家族向けのおすすめ
        intent_mode=RECOMMENDATION
        party_size=4
        budget_upper_bound=4000
        child_count=1
        rationale=人数と予算に合う構成を優先しました。
        recent_order=Teriyaki Chicken Box""");
    }

    @Test
    void keepsRefinementAndDirectItemHintWhenProvided() {
    String rendered = MenuAgentUserPrompt.from(new MenuSuggestionRequest(
        "session-1",
        "照り焼きセットで",
                "野菜多め",
        null,
        new MenuGroundingContext(
            "DIRECT_ITEM",
            "照り焼きセット",
            null,
            null,
            null,
            "商品名らしい指定があるため catalog grounding に直接渡します。")))
                .render();

        assertThat(rendered).isEqualTo(String.join("\n",
            "query=照り焼きセットで",
            "intent_mode=DIRECT_ITEM",
            "direct_item_hint=照り焼きセット",
            "rationale=商品名らしい指定があるため catalog grounding に直接渡します。",
            "refinement=野菜多め"));
    }
}