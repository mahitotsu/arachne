package com.mahitotsu.arachne.samples.delivery.paymentservice.application;

import static com.mahitotsu.arachne.samples.delivery.paymentservice.domain.PaymentTypes.*;

import java.math.RoundingMode;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.mahitotsu.arachne.samples.delivery.paymentservice.infrastructure.PaymentProfileRepository;

@Service
public class PaymentApplicationService {

    private final PaymentProfileRepository repository;

    PaymentApplicationService(PaymentProfileRepository repository) {
        this.repository = repository;
    }

    public PaymentPrepareResponse prepare(PaymentPrepareRequest request) {
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