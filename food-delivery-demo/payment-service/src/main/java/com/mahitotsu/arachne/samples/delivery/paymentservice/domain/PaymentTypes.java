package com.mahitotsu.arachne.samples.delivery.paymentservice.domain;

import java.math.BigDecimal;

public final class PaymentTypes {

    private PaymentTypes() {
    }

    public record PaymentPrepareRequest(String sessionId, String message, BigDecimal total, boolean confirmRequested) {
    }

    public record PaymentPrepareResponse(
            String service,
            String agent,
            String headline,
            String summary,
            String selectedMethod,
            BigDecimal total,
            String paymentStatus,
            boolean charged,
            String authorizationId) {
    }

    public record PaymentProfile(String methodCode, String methodLabel, String note) {
    }
}