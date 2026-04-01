package io.arachne.samples.marketplace.workflowservice;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import io.arachne.samples.marketplace.workflowservice.DownstreamContracts.EscrowEvidenceSummary;
import io.arachne.samples.marketplace.workflowservice.DownstreamContracts.RiskReviewSummary;
import io.arachne.samples.marketplace.workflowservice.DownstreamContracts.ShipmentEvidenceSummary;
import io.arachne.samples.marketplace.workflowservice.WorkflowContracts.EvidenceView;
import io.arachne.samples.marketplace.workflowservice.WorkflowContracts.Recommendation;
import io.arachne.samples.marketplace.workflowservice.WorkflowContracts.StartWorkflowCommand;
import io.arachne.samples.marketplace.workflowservice.WorkflowContracts.WorkflowActivity;

interface WorkflowRuntimeAdapter {

    String POLICY_REFERENCE = "policy://marketplace/disputes/item-not-received";
    BigDecimal AUTOMATED_REFUND_THRESHOLD = BigDecimal.valueOf(100);

    StartAssessment assessStart(StartWorkflowCommand command, RawEvidence rawEvidence, OffsetDateTime now);

    record RawEvidence(
            ShipmentEvidenceSummary shipment,
            EscrowEvidenceSummary escrow,
            RiskReviewSummary risk) {
    }

    record StartAssessment(
            Recommendation recommendation,
            EvidenceView evidence,
            List<WorkflowActivity> activities) {
    }
}