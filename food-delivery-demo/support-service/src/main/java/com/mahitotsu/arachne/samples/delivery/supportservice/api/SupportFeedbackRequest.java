package com.mahitotsu.arachne.samples.delivery.supportservice.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Support feedback intake request.")
public record SupportFeedbackRequest(
	@Schema(description = "Related order identifier when feedback targets a specific order.") String orderId,
	@Schema(description = "Optional customer rating associated with the feedback.") Integer rating,
	@Schema(description = "Natural-language feedback or issue description.", example = "配送が遅かったです") String message) {
}