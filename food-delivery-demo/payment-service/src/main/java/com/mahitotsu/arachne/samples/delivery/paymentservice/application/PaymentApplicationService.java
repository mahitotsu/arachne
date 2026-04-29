package com.mahitotsu.arachne.samples.delivery.paymentservice.application;

import static com.mahitotsu.arachne.samples.delivery.paymentservice.domain.PaymentTypes.*;

import java.math.RoundingMode;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.mahitotsu.arachne.samples.delivery.paymentservice.infrastructure.PaymentProfileRepository;

@Service
public class PaymentApplicationService {

    private final PaymentProfileRepository repository;
    private final PaymentExecutionHistoryStore historyStore;

    PaymentApplicationService(PaymentProfileRepository repository, PaymentExecutionHistoryStore historyStore) {
        this.repository = repository;
        this.historyStore = historyStore;
    }

    public PaymentPrepareResponse prepare(PaymentPrepareRequest request) {
        long startedAt = System.nanoTime();
        PaymentProfile profile = repository.profileFor(request.instruction());
        boolean charged = request.confirmRequested();
        String authorizationId = charged ? "pay-" + UUID.randomUUID().toString().substring(0, 8) : null;
        String paymentStatus = charged ? "CHARGED" : "READY";
        String summary = repository.summarize(profile, charged);
        PaymentPrepareResponse response = new PaymentPrepareResponse(
                "payment-service",
                "payment-service",
                charged ? "支払い処理が完了しました" : "支払方法の準備が完了しました",
                summary,
                profile.methodLabel(),
                request.total().setScale(2, RoundingMode.HALF_UP),
                paymentStatus,
                charged,
                authorizationId);
            historyStore.append(
                request.sessionId(),
                charged ? "confirm-payment" : "prepare-payment",
                "success",
                (System.nanoTime() - startedAt) / 1_000_000,
                response.headline(),
                response.summary());
            return response;
    }
}