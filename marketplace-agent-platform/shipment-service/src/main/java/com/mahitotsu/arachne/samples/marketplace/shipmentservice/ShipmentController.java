package com.mahitotsu.arachne.samples.marketplace.shipmentservice;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/shipment")
class ShipmentController {

    private final ShipmentApplicationService applicationService;

    ShipmentController(ShipmentApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping("/evidence-summary")
    ShipmentContracts.ShipmentEvidenceSummary evidenceSummary(@RequestBody ShipmentContracts.ShipmentEvidenceRequest request) {
        return applicationService.evidenceSummary(request);
    }
}