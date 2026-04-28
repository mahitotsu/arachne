package com.mahitotsu.arachne.samples.delivery.deliveryservice.application;

import java.util.List;

import com.mahitotsu.arachne.samples.delivery.deliveryservice.domain.DeliveryTypes.DeliveryPreference;
import com.mahitotsu.arachne.samples.delivery.deliveryservice.domain.DeliveryTypes.DeliveryPreferenceInput;
import com.mahitotsu.arachne.samples.delivery.deliveryservice.domain.DeliveryTypes.DeliveryQuoteRequest;

record DeliveryAgentUserPrompt(DeliveryPreference priority, String deliveryNotes, List<String> itemNames) {

    static DeliveryAgentUserPrompt from(DeliveryQuoteRequest request) {
        DeliveryPreferenceInput preference = request.preference();
        return new DeliveryAgentUserPrompt(
                preference == null ? null : preference.priority(),
                preference == null ? "" : preference.rawMessage(),
                request.itemNames());
    }

    String render() {
        List<String> safeItemNames = itemNames == null ? List.of() : itemNames;
        return "delivery_priority=" + (priority == null ? "" : priority.name())
                + "\ndelivery_notes=" + (deliveryNotes == null ? "" : deliveryNotes.trim())
                + "\nitem_names=" + String.join(",", safeItemNames);
    }
}