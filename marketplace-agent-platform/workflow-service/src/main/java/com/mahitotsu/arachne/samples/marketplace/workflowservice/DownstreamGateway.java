package com.mahitotsu.arachne.samples.marketplace.workflowservice;

import com.mahitotsu.arachne.samples.marketplace.workflowservice.DownstreamContracts.EscrowEvidenceRequest;
import com.mahitotsu.arachne.samples.marketplace.workflowservice.DownstreamContracts.EscrowEvidenceSummary;
import com.mahitotsu.arachne.samples.marketplace.workflowservice.DownstreamContracts.EscrowSpecialistReview;
import com.mahitotsu.arachne.samples.marketplace.workflowservice.DownstreamContracts.EscrowSpecialistReviewRequest;
import com.mahitotsu.arachne.samples.marketplace.workflowservice.DownstreamContracts.ExecuteSettlementCommand;
import com.mahitotsu.arachne.samples.marketplace.workflowservice.DownstreamContracts.NotificationDispatchCommand;
import com.mahitotsu.arachne.samples.marketplace.workflowservice.DownstreamContracts.NotificationDispatchResult;
import com.mahitotsu.arachne.samples.marketplace.workflowservice.DownstreamContracts.RiskCaseReviewRequest;
import com.mahitotsu.arachne.samples.marketplace.workflowservice.DownstreamContracts.RiskReviewSummary;
import com.mahitotsu.arachne.samples.marketplace.workflowservice.DownstreamContracts.RiskSpecialistReview;
import com.mahitotsu.arachne.samples.marketplace.workflowservice.DownstreamContracts.RiskSpecialistReviewRequest;
import com.mahitotsu.arachne.samples.marketplace.workflowservice.DownstreamContracts.SettlementOutcome;
import com.mahitotsu.arachne.samples.marketplace.workflowservice.DownstreamContracts.ShipmentEvidenceRequest;
import com.mahitotsu.arachne.samples.marketplace.workflowservice.DownstreamContracts.ShipmentEvidenceSummary;
import com.mahitotsu.arachne.samples.marketplace.workflowservice.DownstreamContracts.ShipmentSpecialistReview;
import com.mahitotsu.arachne.samples.marketplace.workflowservice.DownstreamContracts.ShipmentSpecialistReviewRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

interface DownstreamGateway {

    ShipmentEvidenceSummary shipmentEvidence(ShipmentEvidenceRequest request);

    ShipmentSpecialistReview shipmentSpecialistReview(ShipmentSpecialistReviewRequest request);

    EscrowEvidenceSummary escrowEvidence(EscrowEvidenceRequest request);

    EscrowSpecialistReview escrowSpecialistReview(EscrowSpecialistReviewRequest request);

    RiskReviewSummary riskReview(RiskCaseReviewRequest request);

    RiskSpecialistReview riskSpecialistReview(RiskSpecialistReviewRequest request);

    SettlementOutcome executeSettlement(ExecuteSettlementCommand command);

    NotificationDispatchResult dispatchNotification(NotificationDispatchCommand command);
}

@Component
class HttpDownstreamGateway implements DownstreamGateway {

    private final RestClient escrowRestClient;
    private final RestClient shipmentRestClient;
    private final RestClient riskRestClient;
    private final RestClient notificationRestClient;

    HttpDownstreamGateway(
            RestClient escrowRestClient,
            RestClient shipmentRestClient,
            RestClient riskRestClient,
            RestClient notificationRestClient) {
        this.escrowRestClient = escrowRestClient;
        this.shipmentRestClient = shipmentRestClient;
        this.riskRestClient = riskRestClient;
        this.notificationRestClient = notificationRestClient;
    }

    @Override
    public ShipmentEvidenceSummary shipmentEvidence(ShipmentEvidenceRequest request) {
        return requireBody(shipmentRestClient.post()
                .uri("/internal/shipment/evidence-summary")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ShipmentEvidenceSummary.class));
    }

    @Override
    public ShipmentSpecialistReview shipmentSpecialistReview(ShipmentSpecialistReviewRequest request) {
        return requireBody(shipmentRestClient.post()
                .uri("/internal/shipment/specialist-review")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ShipmentSpecialistReview.class));
    }

    @Override
    public EscrowEvidenceSummary escrowEvidence(EscrowEvidenceRequest request) {
        return requireBody(escrowRestClient.post()
                .uri("/internal/escrow/evidence-summary")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(EscrowEvidenceSummary.class));
    }

    @Override
    public EscrowSpecialistReview escrowSpecialistReview(EscrowSpecialistReviewRequest request) {
        return requireBody(escrowRestClient.post()
                .uri("/internal/escrow/specialist-review")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(EscrowSpecialistReview.class));
    }

    @Override
    public RiskReviewSummary riskReview(RiskCaseReviewRequest request) {
        return requireBody(riskRestClient.post()
                .uri("/internal/risk/case-review")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(RiskReviewSummary.class));
    }

    @Override
    public RiskSpecialistReview riskSpecialistReview(RiskSpecialistReviewRequest request) {
        return requireBody(riskRestClient.post()
                .uri("/internal/risk/specialist-review")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(RiskSpecialistReview.class));
    }

    @Override
    public SettlementOutcome executeSettlement(ExecuteSettlementCommand command) {
        return requireBody(escrowRestClient.post()
                .uri("/internal/escrow/settlement-actions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(command)
                .retrieve()
                .body(SettlementOutcome.class));
    }

    @Override
    public NotificationDispatchResult dispatchNotification(NotificationDispatchCommand command) {
        return requireBody(notificationRestClient.post()
                .uri("/internal/notifications/case-outcome")
                .contentType(MediaType.APPLICATION_JSON)
                .body(command)
                .retrieve()
                .body(NotificationDispatchResult.class));
    }

    private <T> T requireBody(T body) {
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "downstream service returned an empty response body");
        }
        return body;
    }
}