package com.mahitotsu.arachne.samples.marketplace.workflowservice;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import com.mahitotsu.arachne.samples.marketplace.workflowservice.DownstreamContracts.EscrowEvidenceSummary;
import com.mahitotsu.arachne.samples.marketplace.workflowservice.DownstreamContracts.RiskReviewSummary;
import com.mahitotsu.arachne.samples.marketplace.workflowservice.DownstreamContracts.ShipmentEvidenceSummary;
import com.mahitotsu.arachne.samples.marketplace.workflowservice.WorkflowContracts.ContinueWorkflowCommand;
import com.mahitotsu.arachne.samples.marketplace.workflowservice.WorkflowContracts.EvidenceView;
import com.mahitotsu.arachne.samples.marketplace.workflowservice.WorkflowContracts.Recommendation;
import com.mahitotsu.arachne.samples.marketplace.workflowservice.WorkflowContracts.ResumeWorkflowCommand;
import com.mahitotsu.arachne.samples.marketplace.workflowservice.WorkflowContracts.StartWorkflowCommand;
import com.mahitotsu.arachne.samples.marketplace.workflowservice.WorkflowContracts.WorkflowActivity;

interface WorkflowRuntimeAdapter {

    String POLICY_REFERENCE = "policy://marketplace/disputes/item-not-received";
    BigDecimal AUTOMATED_REFUND_THRESHOLD = BigDecimal.valueOf(100);

        default boolean requiresPrefetchedEvidence() {
                return true;
        }

    StartAssessment assessStart(StartWorkflowCommand command, RawEvidence rawEvidence, OffsetDateTime now);

        default FollowUpAssessment continueWorkflow(WorkflowSessionState state, ContinueWorkflowCommand command, OffsetDateTime now) {
                return new FollowUpAssessment(state.currentRecommendation(), List.of());
        }

        default Optional<ApprovalPause> pauseForApproval(StartWorkflowCommand command, StartAssessment assessment) {
                return Optional.empty();
        }

        default Optional<ApprovalResume> resumeApproval(WorkflowSessionState state, ResumeWorkflowCommand command) {
                return Optional.empty();
        }

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

        record FollowUpAssessment(
                Recommendation recommendation,
                List<WorkflowActivity> activities) {
        }

        record ApprovalPause(String sessionId, String interruptId, List<WorkflowActivity> activities) {
        }

        record ApprovalResume(String message) {
        }
}