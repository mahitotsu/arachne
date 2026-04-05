package com.mahitotsu.arachne.samples.marketplace.workflowservice;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mahitotsu.arachne.samples.marketplace.workflowservice.WorkflowContracts.ApprovalStateView;
import com.mahitotsu.arachne.samples.marketplace.workflowservice.WorkflowContracts.ApprovalStatus;
import com.mahitotsu.arachne.samples.marketplace.workflowservice.WorkflowContracts.ContinueWorkflowCommand;
import com.mahitotsu.arachne.samples.marketplace.workflowservice.WorkflowContracts.OutcomeView;
import com.mahitotsu.arachne.samples.marketplace.workflowservice.WorkflowContracts.Recommendation;
import com.mahitotsu.arachne.samples.marketplace.workflowservice.WorkflowContracts.ResumeWorkflowCommand;
import com.mahitotsu.arachne.samples.marketplace.workflowservice.WorkflowContracts.StartWorkflowCommand;
import com.mahitotsu.arachne.samples.marketplace.workflowservice.WorkflowContracts.WorkflowActivity;
import com.mahitotsu.arachne.samples.marketplace.workflowservice.WorkflowContracts.WorkflowProgressUpdate;
import com.mahitotsu.arachne.samples.marketplace.workflowservice.WorkflowContracts.WorkflowResumeResult;
import com.mahitotsu.arachne.samples.marketplace.workflowservice.WorkflowContracts.WorkflowStartResult;
import com.mahitotsu.arachne.samples.marketplace.workflowservice.WorkflowContracts.WorkflowStatus;

@Service
class WorkflowApplicationService {

    private final Clock clock;
    private final DownstreamGateway downstreamGateway;
    private final WorkflowSessionRepository workflowSessionRepository;
    private final WorkflowRuntimeAdapter workflowRuntimeAdapter;
                private final OperatorAuthorizationContextHolder operatorAuthorizationContextHolder;
                private final ObjectMapper objectMapper;

    WorkflowApplicationService(
            Clock clock,
            DownstreamGateway downstreamGateway,
            WorkflowSessionRepository workflowSessionRepository,
                        WorkflowRuntimeAdapter workflowRuntimeAdapter,
                        OperatorAuthorizationContextHolder operatorAuthorizationContextHolder,
                        ObjectMapper objectMapper) {
        this.clock = clock;
        this.downstreamGateway = downstreamGateway;
        this.workflowSessionRepository = workflowSessionRepository;
                this.workflowRuntimeAdapter = workflowRuntimeAdapter;
                this.operatorAuthorizationContextHolder = operatorAuthorizationContextHolder;
                this.objectMapper = objectMapper;
    }

    WorkflowStartResult start(StartWorkflowCommand command) {
        var now = now();
        var rawEvidence = collectRawEvidence(
                command.caseId(),
                command.caseType(),
                command.orderId(),
                command.initialMessage(),
                command.amount(),
                command.currency(),
                command.operatorId(),
                command.operatorRole());
        var assessment = withOperatorAuthorizationContext(
                command.operatorId(),
                command.operatorRole(),
                () -> workflowRuntimeAdapter.assessStart(command, rawEvidence, now));
        boolean approvalRequired = assessment.recommendation() != Recommendation.PENDING_MORE_EVIDENCE;
        var approvalPause = approvalRequired
                ? withOperatorAuthorizationContext(
                        command.operatorId(),
                        command.operatorRole(),
                        () -> workflowRuntimeAdapter.pauseForApproval(command, assessment).orElse(null))
                : null;
                List<WorkflowActivity> startActivities = assessment.activities();
                if (approvalPause != null && !approvalPause.activities().isEmpty()) {
                        startActivities = new java.util.ArrayList<>(assessment.activities());
                        startActivities.addAll(approvalPause.activities());
                }
                var state = new WorkflowSessionState(
                command.caseId(),
                command.caseType(),
                command.orderId(),
                command.amount(),
                command.currency(),
                approvalRequired ? WorkflowStatus.AWAITING_APPROVAL : WorkflowStatus.GATHERING_EVIDENCE,
                assessment.recommendation(),
                assessment.evidence(),
                approvalRequired ? pendingApproval(now) : approvalNotRequired(),
                null,
                approvalPause == null ? null : approvalPause.sessionId(),
                approvalPause == null ? null : approvalPause.interruptId());
        workflowSessionRepository.save(state);
        return new WorkflowStartResult(
                state.workflowStatus(),
                state.currentRecommendation(),
                state.evidence(),
                state.approvalState(),
                state.outcome(),
                startActivities);
    }

    WorkflowProgressUpdate continueWorkflow(String caseId, ContinueWorkflowCommand command) {
        var state = workflowSessionRepository.find(caseId).orElseThrow(() -> notFound(caseId));
        var now = now();
                var followUp = withOperatorAuthorizationContext(
                                command.operatorId(),
                                command.operatorRole(),
                                () -> workflowRuntimeAdapter.continueWorkflow(state, command, now));
                var updatedState = new WorkflowSessionState(
                                state.caseId(),
                                state.caseType(),
                                state.orderId(),
                                state.amount(),
                                state.currency(),
                                state.workflowStatus(),
                                followUp.recommendation(),
                                state.evidence(),
                                state.approvalState(),
                                state.outcome(),
                                state.approvalRuntimeSessionId(),
                                state.approvalInterruptId());
        workflowSessionRepository.save(updatedState);
        return new WorkflowProgressUpdate(
                updatedState.workflowStatus(),
                updatedState.currentRecommendation(),
                updatedState.evidence(),
                updatedState.approvalState(),
                updatedState.outcome(),
                followUp.activities().isEmpty()
                                ? List.of(activity(
                                                now,
                                                "OPERATOR_REQUEST_COMPLETED",
                                                "workflow-service",
                                                "Workflow kept the case on the current recommendation after operator follow-up.",
                                                Map.of(
                                                                "type", "workflow_completion",
                                                                "instruction", command.message(),
                                                                "operatorId", command.operatorId(),
                                                                "operatorRole", command.operatorRole(),
                                                                "recommendation", updatedState.currentRecommendation().name(),
                                                                "workflowStatus", updatedState.workflowStatus().name())))
                                : followUp.activities());
    }

    WorkflowResumeResult resume(String caseId, ResumeWorkflowCommand command) {
        var state = workflowSessionRepository.find(caseId).orElseThrow(() -> notFound(caseId));
        var now = now();
                withOperatorAuthorizationContext(command.actorId(), command.actorRole(), () -> {
                        resumeNativeApprovalIfPresent(state, command);
                        return null;
                });
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
                            settlementOutcome.summary()),
                    null,
                    null);
            workflowSessionRepository.save(updatedState);
            return new WorkflowResumeResult(
                    updatedState.workflowStatus(),
                    updatedState.currentRecommendation(),
                    updatedState.evidence(),
                    updatedState.approvalState(),
                    updatedState.outcome(),
                    List.of(
                            activity(now, "SETTLEMENT_COMPLETED", "escrow-service", settlementMessage(updatedState.currentRecommendation())),
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
                null,
                null,
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

        private void resumeNativeApprovalIfPresent(WorkflowSessionState state, ResumeWorkflowCommand command) {
                if (state.approvalRuntimeSessionId() == null || state.approvalInterruptId() == null) {
                        return;
                }
                workflowRuntimeAdapter.resumeApproval(state, command)
                                .orElseThrow(() -> new IllegalStateException("Approval runtime session is missing native resume handling."));
        }

        private WorkflowRuntimeAdapter.RawEvidence collectRawEvidence(
            String caseId,
            String caseType,
            String orderId,
                    String disputeSummary,
            BigDecimal amount,
            String currency,
            String operatorId,
            String operatorRole) {
                var shipment = downstreamGateway.shipmentEvidence(new DownstreamContracts.ShipmentEvidenceRequest(caseId, caseType, disputeSummary, orderId));
        var escrow = downstreamGateway.escrowEvidence(new DownstreamContracts.EscrowEvidenceRequest(
                caseId,
                caseType,
                orderId,
                        disputeSummary,
                amount,
                currency,
                operatorId,
                operatorRole));
                var risk = downstreamGateway.riskReview(new DownstreamContracts.RiskCaseReviewRequest(caseId, caseType, orderId, disputeSummary, operatorRole));
        return new WorkflowRuntimeAdapter.RawEvidence(shipment, escrow, risk);
    }

    private ApprovalStateView pendingApproval(OffsetDateTime now) {
        return new ApprovalStateView(true, ApprovalStatus.PENDING_FINANCE_CONTROL, "FINANCE_CONTROL", now, null, null, null);
    }

        private ApprovalStateView approvalNotRequired() {
                return new ApprovalStateView(false, ApprovalStatus.NOT_REQUIRED, null, null, null, null, null);
        }

    private String settlementMessage(Recommendation recommendation) {
        if (recommendation == Recommendation.REFUND) {
            return "Escrow executed the refund after finance control approval.";
        }
        return "Escrow recorded the continued hold.";
    }

        private WorkflowActivity activity(OffsetDateTime timestamp, String kind, String source, String message) {
                return activity(timestamp, kind, source, message, Map.of());
        }

        private WorkflowActivity activity(OffsetDateTime timestamp, String kind, String source, String message, Map<String, Object> payload) {
                return new WorkflowActivity(kind, source, message, writePayload(payload), timestamp);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

        private <T> T withOperatorAuthorizationContext(String operatorId, String operatorRole, Supplier<T> action) {
                OperatorAuthorizationContext previous = operatorAuthorizationContextHolder.current();
                operatorAuthorizationContextHolder.restore(new OperatorAuthorizationContext(operatorId, operatorRole));
                try {
                        return action.get();
                } finally {
                        operatorAuthorizationContextHolder.restore(previous);
                }
        }

        private ResponseStatusException notFound(String caseId) {
                return new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow session not found: " + caseId);
        }

        private String writePayload(Map<String, Object> payload) {
                try {
                        return objectMapper.writeValueAsString(payload);
                } catch (JsonProcessingException exception) {
                        throw new IllegalStateException("Failed to serialize workflow activity payload", exception);
                }
        }
}