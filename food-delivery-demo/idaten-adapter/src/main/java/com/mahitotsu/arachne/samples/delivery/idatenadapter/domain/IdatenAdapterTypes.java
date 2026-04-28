package com.mahitotsu.arachne.samples.delivery.idatenadapter.domain;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

public final class IdatenAdapterTypes {

	private IdatenAdapterTypes() {
	}

	@Schema(description = "Idaten ETA quote request.")
	public record AdapterEtaRequest(
			@Schema(description = "Item names included in the order draft.") List<String> itemNames,
			@Schema(description = "Delivery context, typically the customer preference for speed or cost.") String context) {
	}

	@Schema(description = "Idaten ETA quote response.")
	public record AdapterEtaResponse(String service, String status, int etaMinutes, String congestion, BigDecimal fee, String note) {
	}

	@Schema(description = "Idaten adapter health response.")
	public record AdapterHealthResponse(String status, String service) {
	}
}