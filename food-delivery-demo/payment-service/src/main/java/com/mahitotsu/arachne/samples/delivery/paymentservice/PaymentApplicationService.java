package com.mahitotsu.arachne.samples.delivery.paymentservice;

import java.math.RoundingMode;
import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
class PaymentApplicationService {

    private final PaymentProfileRepository repository;

    PaymentApplicationService(PaymentProfileRepository repository) {
        this.repository = repository;
    }

    PaymentPrepareResponse prepare(PaymentPrepareRequest request) {
        PaymentProfile profile = repository.profileFor(request.message());
        boolean charged = request.confirmRequested();
        String authorizationId = charged ? "pay-" + UUID.randomUUID().toString().substring(0, 8) : null;
        String paymentStatus = charged ? "CHARGED" : "READY";
        String summary = repository.summarize(profile, charged);
        return new PaymentPrepareResponse(
                "payment-service",
                "payment-service",
                charged ? "支払い処理が完了しました" : "支払方法の準備が完了しました",
                summary,
                profile.methodLabel(),
                request.total().setScale(2, RoundingMode.HALF_UP),
                paymentStatus,
                charged,
                authorizationId);
    }
}