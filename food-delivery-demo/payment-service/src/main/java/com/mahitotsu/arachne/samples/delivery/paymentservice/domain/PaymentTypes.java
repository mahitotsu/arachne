package com.mahitotsu.arachne.samples.delivery.paymentservice.domain;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

public final class PaymentTypes {

    private PaymentTypes() {
    }

        @Schema(description = "支払い準備要求です。")
        public record PaymentPrepareRequest(
            @Schema(description = "親ワークフローの相関 ID。") String sessionId,
            @Schema(description = "自然言語の支払い指示またはチェックアウト指示。", example = "この内容でApple Payで確定") String message,
            @Schema(description = "payment-service が準備または課金する決定論的な合計金額。") BigDecimal total,
            @Schema(description = "支払いを即時実行するかどうか。") boolean confirmRequested) {
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