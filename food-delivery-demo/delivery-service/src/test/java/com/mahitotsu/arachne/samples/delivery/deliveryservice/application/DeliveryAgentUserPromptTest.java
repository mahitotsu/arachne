package com.mahitotsu.arachne.samples.delivery.deliveryservice.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.mahitotsu.arachne.samples.delivery.deliveryservice.domain.DeliveryTypes.DeliveryQuoteRequest;

class DeliveryAgentUserPromptTest {

    @Test
    void rendersCustomerMessageAndItemNamesAsNamedFields() {
        String rendered = DeliveryAgentUserPrompt.from(new DeliveryQuoteRequest(
                "session-1",
                "できるだけ早く届けて",
                List.of("Teriyaki Chicken Box", "Lemon Soda")))
                .render();

        assertThat(rendered).isEqualTo("""
                customer_message=できるだけ早く届けて
                item_names=Teriyaki Chicken Box,Lemon Soda""");
    }
}