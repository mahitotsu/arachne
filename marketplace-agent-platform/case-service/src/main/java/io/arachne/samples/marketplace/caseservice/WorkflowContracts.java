package io.arachne.samples.marketplace.caseservice;

import io.arachne.samples.marketplace.caseservice.CaseContracts.ApprovalStateView;
import io.arachne.samples.marketplace.caseservice.CaseContracts.CaseStatus;
import io.arachne.samples.marketplace.caseservice.CaseContracts.EvidenceView;
import io.arachne.samples.marketplace.caseservice.CaseContracts.OutcomeView;
import io.arachne.samples.marketplace.caseservice.CaseContracts.Recommendation;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class WorkflowContracts {

    private WorkflowContracts() {
    }

    public record StartWorkflowCommand(
            String caseId,
            String caseType,
            String orderId,
            BigDecimal amount,
            String currency,
            String initialMessage,
            String operatorId,
            String operatorRole,
            OffsetDateTime requestedAt) {
    }

    public record ContinueWorkflowCommand(
            String caseId,
            String message,
            String operatorId,
            String operatorRole,
            OffsetDateTime requestedAt) {
    }

    public record ResumeWorkflowCommand(
            String caseId,
            String decision,
            String comment,
            String actorId,
            String actorRole,
            OffsetDateTime requestedAt) {
    }

    public record WorkflowActivity(
            String kind,
            String source,
            String message,
            String structuredPayload,
            OffsetDateTime timestamp) {
    }

    public record WorkflowStartResult(
            CaseStatus workflowStatus,
            Recommendation currentRecommendation,
            EvidenceView evidence,
            ApprovalStateView approvalState,
            OutcomeView outcome,
            List<WorkflowActivity> activities) {
    }

    public record WorkflowProgressUpdate(
            CaseStatus workflowStatus,
            Recommendation currentRecommendation,
            EvidenceView evidence,
            ApprovalStateView approvalState,
            OutcomeView outcome,
            List<WorkflowActivity> activities) {
    }

    public record WorkflowResumeResult(
            CaseStatus workflowStatus,
            Recommendation currentRecommendation,
            EvidenceView evidence,
            ApprovalStateView approvalState,
            OutcomeView outcome,
            List<WorkflowActivity> activities,
            String message) {
    }
}