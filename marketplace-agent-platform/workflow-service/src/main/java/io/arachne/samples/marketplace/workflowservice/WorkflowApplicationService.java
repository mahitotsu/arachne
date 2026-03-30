package io.arachne.samples.marketplace.workflowservice;

import java.math.BigDecimal;
import io.arachne.samples.marketplace.workflowservice.WorkflowContracts.ApprovalStateView;
import io.arachne.samples.marketplace.workflowservice.WorkflowContracts.ApprovalStatus;
import io.arachne.samples.marketplace.workflowservice.WorkflowContracts.ContinueWorkflowCommand;
import io.arachne.samples.marketplace.workflowservice.WorkflowContracts.EvidenceView;
import io.arachne.samples.marketplace.workflowservice.WorkflowContracts.OutcomeView;
import io.arachne.samples.marketplace.workflowservice.WorkflowContracts.Recommendation;
import io.arachne.samples.marketplace.workflowservice.WorkflowContracts.ResumeWorkflowCommand;
import io.arachne.samples.marketplace.workflowservice.WorkflowContracts.StartWorkflowCommand;
import io.arachne.samples.marketplace.workflowservice.WorkflowContracts.WorkflowActivity;
import io.arachne.samples.marketplace.workflowservice.WorkflowContracts.WorkflowProgressUpdate;
import io.arachne.samples.marketplace.workflowservice.WorkflowContracts.WorkflowResumeResult;
import io.arachne.samples.marketplace.workflowservice.WorkflowContracts.WorkflowStartResult;
import io.arachne.samples.marketplace.workflowservice.WorkflowContracts.WorkflowStatus;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
class WorkflowApplicationService {

    private static final String POLICY_REFERENCE = "policy://marketplace/disputes/item-not-received";

    private final Clock clock;
    private final DownstreamGateway downstreamGateway;
        private final WorkflowSessionRepository workflowSessionRepository;

        WorkflowApplicationService(
                        Clock clock,
                        DownstreamGateway downstreamGateway,
                        WorkflowSessionRepository workflowSessionRepository) {
        this.clock = clock;
        this.downstreamGateway = downstreamGateway;
                this.workflowSessionRepository = workflowSessionRepository;
    }

    WorkflowStartResult start(StartWorkflowCommand command) {
        var now = now();
        var evidence = collectEvidence(
            command.caseId(),
            command.caseType(),
            command.orderId(),
            command.amount(),
            command.currency(),
            command.operatorId(),
            command.operatorRole());
        var state = new WorkflowSessionState(
                command.caseId(),
                command.caseType(),
                command.orderId(),
                command.amount(),
                command.currency(),
                WorkflowStatus.AWAITING_APPROVAL,
                Recommendation.CONTINUED_HOLD,
                evidence,
                pendingApproval(now),
                null);
        workflowSessionRepository.save(state);
        return new WorkflowStartResult(
                state.workflowStatus(),
                state.currentRecommendation(),
                state.evidence(),
                state.approvalState(),
                state.outcome(),
                List.of(
                        activity(now, "DELEGATION_STARTED", "workflow-service", "Workflow service started evidence gathering for the new case."),
                        activity(now.plusSeconds(1), "EVIDENCE_RECEIVED", "workflow-service", "Shipment, escrow, and risk evidence are available for recommendation building."),
                        activity(now.plusSeconds(2), "APPROVAL_REQUESTED", "workflow-service", "Finance control approval is required for settlement progression.")));
    }

    WorkflowProgressUpdate continueWorkflow(String caseId, ContinueWorkflowCommand command) {
        var state = workflowSessionRepository.find(caseId).orElseThrow(() -> notFound(caseId));
        var now = now();
        workflowSessionRepository.save(state);
        return new WorkflowProgressUpdate(
                state.workflowStatus(),
                state.currentRecommendation(),
                state.evidence(),
                state.approvalState(),
                state.outcome(),
                List.of(activity(now, "RECOMMENDATION_UPDATED", "workflow-service", "Workflow kept the case on the current recommendation after operator follow-up.")));
    }

    WorkflowResumeResult resume(String caseId, ResumeWorkflowCommand command) {
        var state = workflowSessionRepository.find(caseId).orElseThrow(() -> notFound(caseId));
        var now = now();
        if ("APPROVE".equalsIgnoreCase(command.decision())) {
            var settlementOutcome = downstreamGateway.executeSettlement(new DownstreamContracts.ExecuteSettlementCommand(
                    caseId,
                    state.currentRecommendation().name(),
                    command.actorId(),
                    command.actorRole(),
                    state.amount(),
                    state.currency()));
            downstreamGateway.dispatchNotification(new DownstreamContracts.NotificationDispatchCommand(
                    caseId,
                    settlementOutcome.outcomeType(),
                    settlementOutcome.outcomeStatus(),
                    settlementOutcome.settlementReference()));
            var updatedState = new WorkflowSessionState(
                    state.caseId(),
                    state.caseType(),
                    state.orderId(),
                    state.amount(),
                    state.currency(),
                    WorkflowStatus.COMPLETED,
                    state.currentRecommendation(),
                    state.evidence(),
                    new ApprovalStateView(true, ApprovalStatus.APPROVED, "FINANCE_CONTROL", state.approvalState().requestedAt(), now, command.actorId(), command.comment()),
                    new OutcomeView(
                            WorkflowContracts.OutcomeType.valueOf(settlementOutcome.outcomeType()),
                            settlementOutcome.outcomeStatus(),
                            settlementOutcome.settledAt(),
                            settlementOutcome.settlementReference(),
                            settlementOutcome.summary()));
            workflowSessionRepository.save(updatedState);
            return new WorkflowResumeResult(
                    updatedState.workflowStatus(),
                    updatedState.currentRecommendation(),
                    updatedState.evidence(),
                    updatedState.approvalState(),
                    updatedState.outcome(),
                    List.of(
                            activity(now, "SETTLEMENT_COMPLETED", "escrow-service", "Escrow recorded the continued hold."),
                            activity(now.plusSeconds(1), "NOTIFICATION_DISPATCHED", "notification-service", "Notification dispatch was queued.")),
                    "Approval accepted and settlement completed.");
        }
        var updatedState = new WorkflowSessionState(
                state.caseId(),
                state.caseType(),
                state.orderId(),
                state.amount(),
                state.currency(),
                WorkflowStatus.GATHERING_EVIDENCE,
                Recommendation.PENDING_MORE_EVIDENCE,
                state.evidence(),
                new ApprovalStateView(true, ApprovalStatus.REJECTED, "FINANCE_CONTROL", state.approvalState().requestedAt(), now, command.actorId(), command.comment()),
                null);
        workflowSessionRepository.save(updatedState);
        return new WorkflowResumeResult(
                updatedState.workflowStatus(),
                updatedState.currentRecommendation(),
                updatedState.evidence(),
                updatedState.approvalState(),
                updatedState.outcome(),
                List.of(activity(now, "RECOMMENDATION_UPDATED", "workflow-service", "Workflow returned to evidence gathering after rejection.")),
                "Approval rejection accepted and workflow returned to evidence gathering.");
    }

    private EvidenceView collectEvidence(
            String caseId,
            String caseType,
            String orderId,
            BigDecimal amount,
            String currency,
            String operatorId,
            String operatorRole) {
        var shipment = downstreamGateway.shipmentEvidence(new DownstreamContracts.ShipmentEvidenceRequest(caseId, caseType, orderId));
        var escrow = downstreamGateway.escrowEvidence(new DownstreamContracts.EscrowEvidenceRequest(
                caseId,
                caseType,
                orderId,
                amount,
                currency,
                operatorId,
                operatorRole));
        var risk = downstreamGateway.riskReview(new DownstreamContracts.RiskCaseReviewRequest(caseId, caseType, orderId, operatorRole));
        return new EvidenceView(
                shipment.milestoneSummary() + " Tracking number: " + shipment.trackingNumber() + ". " + shipment.shippingExceptionSummary(),
                escrow.summary(),
                risk.summary(),
                POLICY_REFERENCE);
    }

    private ApprovalStateView pendingApproval(OffsetDateTime now) {
        return new ApprovalStateView(true, ApprovalStatus.PENDING_FINANCE_CONTROL, "FINANCE_CONTROL", now, null, null, null);
    }

    private WorkflowActivity activity(OffsetDateTime timestamp, String kind, String source, String message) {
        return new WorkflowActivity(kind, source, message, "{}", timestamp);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

        private ResponseStatusException notFound(String caseId) {
                return new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow session not found: " + caseId);
        }
}