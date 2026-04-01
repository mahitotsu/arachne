package com.mahitotsu.arachne.samples.marketplace.escrowservice;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/escrow")
class EscrowController {

    private final EscrowApplicationService applicationService;

    EscrowController(EscrowApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping("/evidence-summary")
    EscrowContracts.EscrowEvidenceSummary evidenceSummary(@RequestBody EscrowContracts.EscrowEvidenceRequest request) {
        return applicationService.evidenceSummary(request);
    }

    @PostMapping("/settlement-actions")
    EscrowContracts.SettlementOutcome settlementActions(@RequestBody EscrowContracts.ExecuteSettlementCommand command) {
        return applicationService.executeSettlement(command);
    }
}