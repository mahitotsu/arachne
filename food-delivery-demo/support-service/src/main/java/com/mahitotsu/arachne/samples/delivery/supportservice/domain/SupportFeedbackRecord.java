package com.mahitotsu.arachne.samples.delivery.supportservice.domain;

public record SupportFeedbackRecord(
        String feedbackId,
        String customerId,
        String orderId,
        String classification,
        String summary,
        boolean escalationRequired,
        String createdAt) {
}