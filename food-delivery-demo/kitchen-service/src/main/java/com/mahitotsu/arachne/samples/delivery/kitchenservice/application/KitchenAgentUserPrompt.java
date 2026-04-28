package com.mahitotsu.arachne.samples.delivery.kitchenservice.application;

import java.util.List;

import com.mahitotsu.arachne.samples.delivery.kitchenservice.domain.KitchenTypes.KitchenCheckRequest;

record KitchenAgentUserPrompt(List<String> itemIds, String customerMessage) {

    static KitchenAgentUserPrompt from(KitchenCheckRequest request) {
        return new KitchenAgentUserPrompt(request.itemIds(), request.message());
    }

    String render() {
        List<String> safeItemIds = itemIds == null ? List.of() : itemIds;
        return "items=" + String.join(",", safeItemIds)
                + "\nmessage=" + (customerMessage == null ? "" : customerMessage.trim());
    }
}