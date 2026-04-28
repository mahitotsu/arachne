package com.mahitotsu.arachne.samples.delivery.deliveryservice.application;

import java.util.List;

import com.mahitotsu.arachne.samples.delivery.deliveryservice.domain.DeliveryTypes.DeliveryQuoteRequest;

record DeliveryAgentUserPrompt(String customerMessage, List<String> itemNames) {

    static DeliveryAgentUserPrompt from(DeliveryQuoteRequest request) {
        return new DeliveryAgentUserPrompt(request.message(), request.itemNames());
    }

    String render() {
        List<String> safeItemNames = itemNames == null ? List.of() : itemNames;
        return "customer_message=" + (customerMessage == null ? "" : customerMessage.trim())
                + "\nitem_names=" + String.join(",", safeItemNames);
    }
}