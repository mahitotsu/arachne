package com.mahitotsu.arachne.samples.delivery.paymentservice.domain;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

public final class PaymentTypes {

    private PaymentTypes() {
    }

    @Schema(description = "支払い準備要求です。")
    public record PaymentPrepareRequest(
            @Schema(description = "親ワークフローの相関 ID。") String sessionId,
            @Schema(description = "支払い方法の希望や補足を表す構造化入力です。") PaymentInstructionInput instruction,
            @Schema(description = "payment-service が準備または課金する決定論的な合計金額。") BigDecimal total,
            @Schema(description = "支払いを即時実行するかどうか。") boolean confirmRequested) {

        public PaymentPrepareRequest {
            if (instruction == null) {
                instruction = new PaymentInstructionInput(null, null);
            }
        }

        public PaymentPrepareRequest(String sessionId, String message, BigDecimal total, boolean confirmRequested) {
            this(sessionId, new PaymentInstructionInput(message, null), total, confirmRequested);
        }
    }

    @Schema(description = "支払い手段の希望と補足メモを表す構造化入力です。")
    public record PaymentInstructionInput(
            @Schema(description = "自由記述の支払い指示や補足。", example = "この内容でApple Payで確定") String rawMessage,
            @Schema(description = "希望する支払い方法。明示されていればそれを優先します。") PaymentMethodPreference requestedMethod) {
    }

    public enum PaymentMethodPreference {
        APPLE_PAY,
        CASH_ON_DELIVERY,
        SAVED_CARD
    }

    @Schema(description = "オーケストレーターへ返す支払い準備結果です。")
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