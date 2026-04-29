package com.mahitotsu.arachne.samples.delivery.supportservice.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "support feedback 受付要求です。")
public record SupportFeedbackRequest(
	@Schema(description = "親ワークフローや chat と結び付ける任意の session ID。") String sessionId,
	@Schema(description = "feedback が特定注文に紐づく場合の関連 orderId。") String orderId,
	@Schema(description = "feedback に付随する任意の customer rating。") Integer rating,
	@Schema(description = "自然言語の feedback または問題説明。", example = "配送が遅かったです") String message) {

	public SupportFeedbackRequest(String orderId, Integer rating, String message) {
		this(null, orderId, rating, message);
	}
}