package com.mahitotsu.arachne.samples.delivery.supportservice.api;

public record SupportFeedbackRequest(String orderId, Integer rating, String message) {
}