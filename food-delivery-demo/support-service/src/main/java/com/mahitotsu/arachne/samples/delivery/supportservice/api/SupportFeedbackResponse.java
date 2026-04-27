package com.mahitotsu.arachne.samples.delivery.supportservice.api;

public record SupportFeedbackResponse(
        String service,
        String agent,
        String headline,
        String summary,
        String classification,
        boolean escalationRequired) {
}