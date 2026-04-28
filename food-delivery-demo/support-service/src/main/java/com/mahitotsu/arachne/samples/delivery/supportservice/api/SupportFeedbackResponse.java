package com.mahitotsu.arachne.samples.delivery.supportservice.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Support feedback classification result.")
public record SupportFeedbackResponse(
        String service,
        String agent,
        String headline,
        String summary,
        String classification,
        boolean escalationRequired) {
}