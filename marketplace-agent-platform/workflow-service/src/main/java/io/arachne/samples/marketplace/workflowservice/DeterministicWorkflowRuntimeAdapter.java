package io.arachne.samples.marketplace.workflowservice;

import java.time.OffsetDateTime;
import java.util.List;

import io.arachne.samples.marketplace.workflowservice.WorkflowContracts.EvidenceView;
import io.arachne.samples.marketplace.workflowservice.WorkflowContracts.Recommendation;
import io.arachne.samples.marketplace.workflowservice.WorkflowContracts.StartWorkflowCommand;
import io.arachne.samples.marketplace.workflowservice.WorkflowContracts.WorkflowActivity;

final class DeterministicWorkflowRuntimeAdapter implements WorkflowRuntimeAdapter {

    @Override
    public StartAssessment assessStart(StartWorkflowCommand command, RawEvidence rawEvidence, OffsetDateTime now) {
        Recommendation recommendation = initialRecommendation(command.caseType(), command.amount());
        EvidenceView evidence = new EvidenceView(
                rawEvidence.shipment().milestoneSummary() + " Tracking number: " + rawEvidence.shipment().trackingNumber() + ". "
                        + rawEvidence.shipment().shippingExceptionSummary(),
                rawEvidence.escrow().summary(),
                rawEvidence.risk().summary(),
                POLICY_REFERENCE);
        return new StartAssessment(
                recommendation,
                evidence,
                List.of(
                        activity(now, "DELEGATION_STARTED", "workflow-service", "Workflow service started evidence gathering for the new case."),
                        activity(now.plusSeconds(1), "EVIDENCE_RECEIVED", "workflow-service", "Shipment, escrow, and risk evidence are available for recommendation building."),
                        activity(now.plusSeconds(2), "RECOMMENDATION_UPDATED", "workflow-service", recommendationMessage(recommendation)),
                        activity(now.plusSeconds(3), "APPROVAL_REQUESTED", "workflow-service", "Finance control approval is required for settlement progression.")));
    }

    private Recommendation initialRecommendation(String caseType, java.math.BigDecimal amount) {
        if ("ITEM_NOT_RECEIVED".equalsIgnoreCase(caseType)
                && amount != null
                && amount.compareTo(AUTOMATED_REFUND_THRESHOLD) <= 0) {
            return Recommendation.REFUND;
        }
        return Recommendation.CONTINUED_HOLD;
    }

    private String recommendationMessage(Recommendation recommendation) {
        if (recommendation == Recommendation.REFUND) {
            return "Workflow recommends a refund after confirming non-delivery and a low-value exposure path.";
        }
        return "Workflow recommends keeping the hold until finance control confirms the next step.";
    }

    private WorkflowActivity activity(OffsetDateTime timestamp, String kind, String source, String message) {
        return new WorkflowActivity(kind, source, message, "{}", timestamp);
    }
}