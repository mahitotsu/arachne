package com.mahitotsu.arachne.samples.delivery.orderservice.domain;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

public final class OrderExecutionHistoryTypes {

    private OrderExecutionHistoryTypes() {
    }

    @Schema(description = "注文ワークフローの session 単位で蓄積した実行履歴です。")
    public record OrderExecutionHistoryResponse(
            @Schema(description = "照会対象のワークフロー session ID。") String sessionId,
            @Schema(description = "時系列順に並んだ実行イベント。") List<OrderExecutionHistoryEntry> events) {
    }

    @Schema(description = "注文ワークフローに紐づく単一の実行イベントです。")
    public record OrderExecutionHistoryEntry(
            @Schema(description = "同一 service 内で単調増加するイベント sequence。") long sequence,
            @Schema(description = "イベント発生時刻。ISO-8601 文字列。") String occurredAt,
            @Schema(description = "イベント種別。workflow / downstream など。") String category,
            @Schema(description = "イベントを出した service 名。") String service,
            @Schema(description = "イベントを出した component 名。") String component,
            @Schema(description = "実行された操作名。") String operation,
            @Schema(description = "success / error などの結果。") String outcome,
            @Schema(description = "処理時間ミリ秒。") long durationMs,
            @Schema(description = "一覧表示向けの短い見出し。") String headline,
            @Schema(description = "入力や出力を要約した詳細テキスト。") String detail) {
    }
}