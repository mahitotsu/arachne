package com.mahitotsu.arachne.samples.delivery.supportservice.domain;

public record FeedbackInsight(
        String feedbackId,
        String orderId,
        String category,
        String summary,
        boolean escalationRequired,
        String createdAt) {
}