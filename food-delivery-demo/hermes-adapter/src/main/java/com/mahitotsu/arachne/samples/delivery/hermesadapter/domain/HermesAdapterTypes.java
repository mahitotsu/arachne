package com.mahitotsu.arachne.samples.delivery.hermesadapter.domain;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

public final class HermesAdapterTypes {

	private HermesAdapterTypes() {
	}

	@Schema(description = "Hermes ETA 見積もり要求です。")
	public record AdapterEtaRequest(
			@Schema(description = "注文下書きに含まれる商品名。") List<String> itemNames,
			@Schema(description = "通常は速さや価格に関する顧客の配送希望を表すコンテキスト。") String context) {
	}

	@Schema(description = "Hermes ETA 見積もり応答です。")
	public record AdapterEtaResponse(String service, String status, int etaMinutes, String congestion, BigDecimal fee, String note) {
	}

	@Schema(description = "Hermes アダプターのヘルス応答です。")
	public record AdapterHealthResponse(String status, String service) {
	}
}