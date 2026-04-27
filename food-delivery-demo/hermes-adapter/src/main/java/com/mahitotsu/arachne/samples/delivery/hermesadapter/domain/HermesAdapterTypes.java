package com.mahitotsu.arachne.samples.delivery.hermesadapter.domain;

import java.math.BigDecimal;
import java.util.List;

public final class HermesAdapterTypes {

	private HermesAdapterTypes() {
	}

	public record AdapterEtaRequest(List<String> itemNames, String context) {
	}

	public record AdapterEtaResponse(String service, String status, int etaMinutes, String congestion, BigDecimal fee, String note) {
	}

	public record AdapterHealthResponse(String status, String service) {
	}
}