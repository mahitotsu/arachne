package com.mahitotsu.arachne.samples.marketplace.shipmentservice;

import com.mahitotsu.arachne.strands.spring.AgentFactory;
import com.mahitotsu.arachne.strands.tool.Tool;
import org.springframework.stereotype.Service;

@Service
class ShipmentApplicationService {

    private final ShipmentRepository repository;
    private final AgentFactory agentFactory;
    private final Tool shipmentRecordLookupTool;

    ShipmentApplicationService(ShipmentRepository repository, AgentFactory agentFactory, Tool shipmentRecordLookupTool) {
        this.repository = repository;
        this.agentFactory = agentFactory;
        this.shipmentRecordLookupTool = shipmentRecordLookupTool;
    }

    ShipmentContracts.ShipmentEvidenceSummary evidenceSummary(ShipmentContracts.ShipmentEvidenceRequest request) {
        repository.ensureShipmentRecord(request.caseId(), request.orderId(), request.caseType(), request.disputeSummary());
        return agentFactory.builder("shipment-agent")
                .tools(shipmentRecordLookupTool)
                .build()
                .run(evidencePrompt(request), ShipmentContracts.ShipmentEvidenceSummary.class);
    }

    ShipmentContracts.ShipmentSpecialistReview specialistReview(ShipmentContracts.ShipmentSpecialistReviewRequest request) {
        repository.ensureShipmentRecord(request.caseId(), request.orderId(), request.caseType(), request.disputeSummary());
        return agentFactory.builder("shipment-agent")
                .tools(shipmentRecordLookupTool)
                .build()
                .run(reviewPrompt(request), ShipmentContracts.ShipmentSpecialistReview.class);
    }

    private String evidencePrompt(ShipmentContracts.ShipmentEvidenceRequest request) {
        return String.join("\n",
                "mode=evidence-summary",
                "caseId=" + blankSafe(request.caseId()),
                "caseType=" + blankSafe(request.caseType()),
                "orderId=" + blankSafe(request.orderId()),
                "disputeSummary=" + blankSafe(request.disputeSummary()),
                "instructions=Call shipment_record_lookup exactly once, then respond through structured_output with trackingNumber, milestoneSummary, deliveryConfidence, and shippingExceptionSummary.");
    }

    private String reviewPrompt(ShipmentContracts.ShipmentSpecialistReviewRequest request) {
        return String.join("\n",
                "mode=specialist-review",
                "caseId=" + blankSafe(request.caseId()),
                "caseType=" + blankSafe(request.caseType()),
                "orderId=" + blankSafe(request.orderId()),
                "disputeSummary=" + blankSafe(request.disputeSummary()),
                "instruction=" + blankSafe(request.instruction()),
                "instructions=Call shipment_record_lookup exactly once, then respond through structured_output with a concise shipment specialist summary in the summary field.");
    }

    private String blankSafe(String value) {
        return value == null ? "" : value;
    }
}