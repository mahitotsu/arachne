package com.mahitotsu.arachne.samples.delivery.idatenadapter.domain;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

public final class IdatenAdapterTypes {

	private IdatenAdapterTypes() {
	}

	@Schema(description = "Idaten ETA 見積もり要求です。")
	public record AdapterEtaRequest(
			@Schema(description = "注文下書きに含まれる商品名。") List<String> itemNames,
			@Schema(description = "配送希望を表す構造化入力です。") DeliveryPreferenceInput preference) {

		public AdapterEtaRequest {
			if (preference == null) {
				preference = new DeliveryPreferenceInput(null, null);
			}

		}

		public AdapterEtaRequest(List<String> itemNames, String context) {
			this(itemNames, new DeliveryPreferenceInput(context, null));
		}
	}

	public record DeliveryPreferenceInput(String rawMessage, DeliveryPriority priority) {
	}

	public enum DeliveryPriority {
		URGENT,
		CHEAP,
		BALANCED
	}

	@Schema(description = "Idaten ETA 見積もり応答です。")
	public record AdapterEtaResponse(String service, String status, int etaMinutes, String congestion, BigDecimal fee, String note) {
	}

	@Schema(description = "Idaten アダプターのヘルス応答です。")
	public record AdapterHealthResponse(String status, String service) {
	}
}