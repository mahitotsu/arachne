package com.mahitotsu.arachne.samples.delivery.menuservice.domain;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

public final class MenuExecutionHistoryTypes {

    private MenuExecutionHistoryTypes() {
    }

    @Schema(description = "menu-service の session 単位実行履歴です。")
    public record MenuExecutionHistoryResponse(
            @Schema(description = "照会対象の session ID。") String sessionId,
            @Schema(description = "時系列順の実行イベント。") List<MenuExecutionHistoryEvent> events) {
    }

    @Schema(description = "menu-service の単一実行イベントです。")
    public record MenuExecutionHistoryEvent(
            @Schema(description = "service 内 sequence。") long sequence,
            @Schema(description = "ISO-8601 timestamp。") String occurredAt,
            @Schema(description = "agent / model / tool の種別。") String category,
            @Schema(description = "service 名。") String service,
            @Schema(description = "agent 名または tool 名。") String component,
            @Schema(description = "操作名。") String operation,
            @Schema(description = "started / success / error / completed など。") String outcome,
            @Schema(description = "処理時間ミリ秒。") long durationMs,
            @Schema(description = "一覧向け見出し。") String headline,
            @Schema(description = "詳細テキスト。") String detail,
            @Schema(description = "利用 token 内訳。agent 完了イベントでのみ設定。") AgentUsageBreakdown usage,
            @Schema(description = "その時点で有効だったスキル名。") List<String> skills) {
    }

    @Schema(description = "agent 実行で観測した usage の内訳です。")
    public record AgentUsageBreakdown(
            @Schema(description = "入力 token 数。") int inputTokens,
            @Schema(description = "出力 token 数。") int outputTokens,
            @Schema(description = "cache read token 数。") int cacheReadTokens,
            @Schema(description = "cache write token 数。") int cacheWriteTokens) {
    }
}