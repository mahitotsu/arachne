package io.arachne.samples.marketplace.caseservice;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class CaseContracts {

    private CaseContracts() {
    }

    public enum CaseStatus {
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

    public record CreateCaseCommand(
            String caseType,
            String orderId,
            BigDecimal amount,
            String currency,
            String initialMessage,
            String operatorId,
            String operatorRole) {
    }

    public record AddCaseMessageCommand(
            String message,
            String operatorId,
            String operatorRole) {
    }

    public record SubmitApprovalCommand(
            String decision,
            String comment,
            String actorId,
            String actorRole) {
    }

    public record CaseListItem(
            String caseId,
            String caseType,
            CaseStatus caseStatus,
            String orderId,
            BigDecimal amount,
            String currency,
            Recommendation currentRecommendation,
            ApprovalStatus approvalStatus,
            String requestedRole,
            OutcomeType outcomeType,
            boolean pendingApproval,
            OffsetDateTime updatedAt) {
    }

    public record CaseSummaryView(
            String caseId,
            String caseType,
            CaseStatus caseStatus,
            String orderId,
            String transactionId,
            BigDecimal amount,
            String currency,
            Recommendation currentRecommendation) {
    }

    public record EvidenceView(
            String shipmentEvidence,
            String escrowEvidence,
            String riskEvidence,
            String policyReference) {
    }

    public record ActivityEvent(
            String eventId,
            String caseId,
            OffsetDateTime timestamp,
            String kind,
            String source,
            String message,
            String structuredPayload) {
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

    public record CaseDetailView(
            String caseId,
            String caseType,
            CaseStatus caseStatus,
            String orderId,
            String transactionId,
            BigDecimal amount,
            String currency,
            Recommendation currentRecommendation,
            CaseSummaryView caseSummary,
            EvidenceView evidence,
            List<ActivityEvent> activityHistory,
            ApprovalStateView approvalState,
            OutcomeView outcome) {
    }

    public record ApprovalSubmissionResult(
            String caseId,
            ApprovalStateView approvalState,
            CaseStatus workflowStatus,
            boolean resumeAccepted,
            String message) {
    }
}