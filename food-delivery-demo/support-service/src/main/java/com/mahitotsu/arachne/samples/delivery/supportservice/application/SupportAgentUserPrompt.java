package com.mahitotsu.arachne.samples.delivery.supportservice.application;

import com.mahitotsu.arachne.samples.delivery.supportservice.api.SupportChatRequest;

record SupportAgentUserPrompt(String inquiry, String customerId) {

    static SupportAgentUserPrompt from(SupportChatRequest request, String customerId) {
        return new SupportAgentUserPrompt(request.message(), customerId);
    }

    String render() {
        return "inquiry=" + (inquiry == null ? "" : inquiry.trim())
                + "\ncustomerId=" + (customerId == null ? "" : customerId.trim());
    }
}