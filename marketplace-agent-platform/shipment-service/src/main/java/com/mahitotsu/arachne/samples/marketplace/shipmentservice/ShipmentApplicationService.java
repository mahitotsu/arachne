package com.mahitotsu.arachne.samples.marketplace.shipmentservice;

import org.springframework.stereotype.Service;

@Service
class ShipmentApplicationService {

    private final ShipmentRepository repository;

    ShipmentApplicationService(ShipmentRepository repository) {
        this.repository = repository;
    }

    ShipmentContracts.ShipmentEvidenceSummary evidenceSummary(ShipmentContracts.ShipmentEvidenceRequest request) {
        repository.ensureShipmentRecord(request.caseId(), request.orderId(), request.caseType(), request.disputeSummary());
        var record = repository.findShipmentRecord(request.caseId())
                .orElseThrow(() -> new IllegalStateException("Shipment record missing after initialization for case " + request.caseId()));
        return new ShipmentContracts.ShipmentEvidenceSummary(
                record.trackingNumber(),
                record.milestoneSummary(),
                record.deliveryConfidence(),
                record.shippingExceptionSummary());
    }
}