package com.mahitotsu.arachne.samples.marketplace.shipmentservice;

final class ShipmentContracts {

    private ShipmentContracts() {
    }

    record ShipmentEvidenceRequest(
            String caseId,
            String caseType,
            String orderId) {
    }

    record ShipmentEvidenceSummary(
            String trackingNumber,
            String milestoneSummary,
            String deliveryConfidence,
            String shippingExceptionSummary) {
    }
}