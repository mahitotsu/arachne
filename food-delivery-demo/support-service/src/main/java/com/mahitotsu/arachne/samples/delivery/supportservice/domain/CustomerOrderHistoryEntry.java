package com.mahitotsu.arachne.samples.delivery.supportservice.domain;

import java.math.BigDecimal;

public record CustomerOrderHistoryEntry(
        String orderId,
        String itemSummary,
        BigDecimal total,
        String etaLabel,
        String paymentStatus,
        String createdAt) {
}