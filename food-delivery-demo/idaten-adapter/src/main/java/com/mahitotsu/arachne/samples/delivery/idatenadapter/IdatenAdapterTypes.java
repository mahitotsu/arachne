package com.mahitotsu.arachne.samples.delivery.idatenadapter;

import java.math.BigDecimal;
import java.util.List;

record AdapterEtaRequest(List<String> itemNames, String context) {
}

record AdapterEtaResponse(String service, String status, int etaMinutes, String congestion, BigDecimal fee, String note) {
}

record AdapterHealthResponse(String status, String service) {
}