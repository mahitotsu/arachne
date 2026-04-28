package com.mahitotsu.arachne.samples.delivery.menuservice.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.mahitotsu.arachne.samples.delivery.menuservice.domain.MenuTypes.MenuSuggestionRequest;

class MenuAgentUserPromptTest {

    @Test
    void rendersQueryRefinementAndRecentOrderAsNamedFields() {
        String rendered = MenuAgentUserPrompt.from(new MenuSuggestionRequest(
                "session-1",
                "家族向けのおすすめ",
                "野菜多め",
                "Teriyaki Chicken Box"))
                .render();

        assertThat(rendered).isEqualTo("""
                query=家族向けのおすすめ
                refinement=野菜多め
                recent_order=Teriyaki Chicken Box""");
    }
}