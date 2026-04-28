package com.mahitotsu.arachne.samples.delivery.deliveryservice.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.mahitotsu.arachne.samples.delivery.deliveryservice.domain.DeliveryTypes.DeliveryPreference;
import com.mahitotsu.arachne.samples.delivery.deliveryservice.domain.DeliveryTypes.DeliveryPreferenceInput;
import com.mahitotsu.arachne.samples.delivery.deliveryservice.domain.DeliveryTypes.DeliveryQuoteRequest;

class DeliveryAgentUserPromptTest {

    @Test
    void rendersPriorityNotesAndItemNamesAsNamedFields() {
        String rendered = DeliveryAgentUserPrompt.from(new DeliveryQuoteRequest(
                "session-1",
                new DeliveryPreferenceInput("できるだけ早く届けて", DeliveryPreference.URGENT),
                List.of("Teriyaki Chicken Box", "Lemon Soda")))
                .render();

        assertThat(rendered).isEqualTo("""
                delivery_priority=URGENT
                delivery_notes=できるだけ早く届けて
                item_names=Teriyaki Chicken Box,Lemon Soda""");
    }
}