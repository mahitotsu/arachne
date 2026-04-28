package com.mahitotsu.arachne.samples.delivery.paymentservice.domain;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

public final class PaymentTypes {

    private PaymentTypes() {
    }

        @Schema(description = "Payment preparation request.")
        public record PaymentPrepareRequest(
            @Schema(description = "Correlation identifier for the parent workflow.") String sessionId,
            @Schema(description = "Natural-language payment or checkout instruction.", example = "この内容でApple Payで確定") String message,
            @Schema(description = "Deterministic checkout total to prepare or charge.") BigDecimal total,
            @Schema(description = "Whether the payment should be executed immediately.") boolean confirmRequested) {
    }

        @Schema(description = "Payment preparation result returned to the orchestrator.")
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