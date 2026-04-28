package com.mahitotsu.arachne.samples.delivery.supportservice.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "認証済み support 問い合わせ要求です。")
public record SupportChatRequest(
	@Schema(description = "support chat を継続するための会話セッション ID。") String sessionId,
	@Schema(description = "support-service へ送る自然言語の問い合わせ。", example = "キャンペーンと配送状況を教えて") String message) {
}