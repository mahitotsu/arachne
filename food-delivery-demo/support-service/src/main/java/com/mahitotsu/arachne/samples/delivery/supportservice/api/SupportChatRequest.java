package com.mahitotsu.arachne.samples.delivery.supportservice.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Authenticated support inquiry request.")
public record SupportChatRequest(
	@Schema(description = "Conversation session identifier for continuing a support chat.") String sessionId,
	@Schema(description = "Natural-language inquiry sent to support-service.", example = "キャンペーンと配送状況を教えて") String message) {
}