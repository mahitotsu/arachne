package io.arachne.samples.marketplace.workflowservice;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class WorkflowContracts {

    private WorkflowContracts() {
    }

    public enum WorkflowStatus {
        OPEN,
        GATHERING_EVIDENCE,
        AWAITING_APPROVAL,
        READY_FOR_SETTLEMENT,
        COMPLETED
    }

    public enum Recommendation {
        REFUND,
        CONTINUED_HOLD,
        PENDING_MORE_EVIDENCE
    }

    public enum ApprovalStatus {
        NOT_REQUIRED,
        PENDING_FINANCE_CONTROL,
        APPROVED,
        REJECTED
    }

    public enum OutcomeType {
        REFUND_EXECUTED,
        CONTINUED_HOLD_RECORDED
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
            String message,
            String operatorId,
            String operatorRole,
            OffsetDateTime requestedAt) {
    }

    public record ResumeWorkflowCommand(
            String decision,
            String comment,
            String actorId,
            String actorRole,
            OffsetDateTime requestedAt) {
    }

    public record EvidenceView(
            String shipmentEvidence,
            String escrowEvidence,
            String riskEvidence,
            String policyReference) {
    }

    public record ApprovalStateView(
            boolean approvalRequired,
            ApprovalStatus approvalStatus,
            String requestedRole,
            OffsetDateTime requestedAt,
            OffsetDateTime decisionAt,
            String decisionBy,
            String comment) {
    }

    public record OutcomeView(
            OutcomeType outcomeType,
            String outcomeStatus,
            OffsetDateTime settledAt,
            String settlementReference,
            String summary) {
    }

    public record WorkflowActivity(
            String kind,
            String source,
            String message,
            String structuredPayload,
            OffsetDateTime timestamp) {
    }

    public record WorkflowStartResult(
            WorkflowStatus workflowStatus,
            Recommendation currentRecommendation,
            EvidenceView evidence,
            ApprovalStateView approvalState,
            OutcomeView outcome,
            List<WorkflowActivity> activities) {
    }

    public record WorkflowProgressUpdate(
            WorkflowStatus workflowStatus,
            Recommendation currentRecommendation,
            EvidenceView evidence,
            ApprovalStateView approvalState,
            OutcomeView outcome,
            List<WorkflowActivity> activities) {
    }

    public record WorkflowResumeResult(
            WorkflowStatus workflowStatus,
            Recommendation currentRecommendation,
            EvidenceView evidence,
            ApprovalStateView approvalState,
            OutcomeView outcome,
            List<WorkflowActivity> activities,
            String message) {
    }
}