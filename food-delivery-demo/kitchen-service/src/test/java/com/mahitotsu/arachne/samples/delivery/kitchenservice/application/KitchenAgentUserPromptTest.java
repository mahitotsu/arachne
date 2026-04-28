package com.mahitotsu.arachne.samples.delivery.kitchenservice.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.mahitotsu.arachne.samples.delivery.kitchenservice.domain.KitchenTypes.KitchenCheckRequest;

class KitchenAgentUserPromptTest {

    @Test
    void rendersItemsAndCustomerMessageAsNamedFields() {
        String rendered = KitchenAgentUserPrompt.from(new KitchenCheckRequest(
                "session-1",
                "子ども向けにしたい",
                List.of("combo-kids", "drink-lemon")))
                .render();

        assertThat(rendered).isEqualTo("""
                items=combo-kids,drink-lemon
                message=子ども向けにしたい""");
    }
}