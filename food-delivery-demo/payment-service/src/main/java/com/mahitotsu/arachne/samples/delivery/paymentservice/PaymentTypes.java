package com.mahitotsu.arachne.samples.delivery.paymentservice;

import java.math.BigDecimal;

record PaymentPrepareRequest(String sessionId, String message, BigDecimal total, boolean confirmRequested) {
}

record PaymentPrepareResponse(
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

record PaymentProfile(String methodCode, String methodLabel, String note) {
}