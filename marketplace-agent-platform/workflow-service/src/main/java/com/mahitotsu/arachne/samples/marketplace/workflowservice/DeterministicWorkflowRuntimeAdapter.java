package com.mahitotsu.arachne.samples.marketplace.workflowservice;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mahitotsu.arachne.samples.marketplace.workflowservice.WorkflowContracts.ContinueWorkflowCommand;
import com.mahitotsu.arachne.samples.marketplace.workflowservice.WorkflowContracts.EvidenceView;
import com.mahitotsu.arachne.samples.marketplace.workflowservice.WorkflowContracts.Recommendation;
import com.mahitotsu.arachne.samples.marketplace.workflowservice.WorkflowContracts.StartWorkflowCommand;
import com.mahitotsu.arachne.samples.marketplace.workflowservice.WorkflowContracts.WorkflowActivity;

final class DeterministicWorkflowRuntimeAdapter implements WorkflowRuntimeAdapter {

    private final ObjectMapper objectMapper;

    DeterministicWorkflowRuntimeAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

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
                activity(
                    now,
                    "DELEGATION_STARTED",
                    "workflow-service",
                    "Workflow service started evidence gathering for the new case.",
                    Map.of(
                        "type", "delegation_start",
                        "phase", "start",
                        "delegatedBy", "workflow-service",
                        "delegatedAgents", List.of("shipment-service", "escrow-service", "risk-service"),
                        "instruction", blankSafe(command.initialMessage()))),
                activity(
                    now.plusSeconds(1),
                    "EVIDENCE_RECEIVED",
                    "workflow-service",
                    "Shipment, escrow, and risk evidence are available for recommendation building.",
                    Map.of(
                        "type", "evidence_bundle",
                        "shipmentEvidence", evidence.shipmentEvidence(),
                        "escrowEvidence", evidence.escrowEvidence(),
                        "riskEvidence", evidence.riskEvidence())),
                activity(
                    now.plusSeconds(2),
                    "RECOMMENDATION_UPDATED",
                    "workflow-service",
                    recommendationMessage(recommendation),
                    Map.of(
                        "type", "workflow_decision",
                        "recommendation", recommendation.name(),
                        "policyReference", POLICY_REFERENCE)),
                activity(
                    now.plusSeconds(3),
                    "APPROVAL_REQUESTED",
                    "workflow-service",
                    "Finance control approval is required for settlement progression.",
                    Map.of(
                        "type", "approval_gate",
                        "requestedRole", "FINANCE_CONTROL",
                        "recommendation", recommendation.name()))));
        }

        @Override
        public FollowUpAssessment continueWorkflow(WorkflowSessionState state, ContinueWorkflowCommand command, OffsetDateTime now) {
        List<String> delegatedAgents = selectDelegates(command.message());
        LinkedHashMap<String, Object> instructionPayload = new LinkedHashMap<>();
        instructionPayload.put("type", "operator_instruction");
        instructionPayload.put("instruction", blankSafe(command.message()));
        instructionPayload.put("operatorId", blankSafe(command.operatorId()));
        instructionPayload.put("operatorRole", blankSafe(command.operatorRole()));
        instructionPayload.put("delegatedAgents", delegatedAgents);
        instructionPayload.put("recommendation", state.currentRecommendation().name());

        String focusSummary = delegatedAgents.contains("shipment-agent")
            ? state.evidence().shipmentEvidence()
            : delegatedAgents.contains("escrow-agent")
                ? state.evidence().escrowEvidence()
                : state.evidence().riskEvidence();

        return new FollowUpAssessment(
            state.currentRecommendation(),
            List.of(
                activity(
                    now,
                    "OPERATOR_INSTRUCTION_RECEIVED",
                    "workflow-service",
                    "Workflow service accepted the operator instruction and routed it to the relevant domain reviewer.",
                    instructionPayload),
                activity(
                    now.plusSeconds(1),
                    "AGENT_RESPONSE",
                    delegatedAgents.getFirst(),
                    "Deterministic follow-up review focused on the requested evidence slice.",
                    Map.of(
                        "type", "agent_response",
                        "agent", delegatedAgents.getFirst(),
                        "focus", delegateFocus(delegatedAgents.getFirst()),
                        "summary", focusSummary,
                        "instruction", blankSafe(command.message()))),
                activity(
                    now.plusSeconds(2),
                    "OPERATOR_REQUEST_COMPLETED",
                    "workflow-service",
                    completionMessage(state.currentRecommendation(), delegatedAgents),
                    Map.of(
                        "type", "workflow_completion",
                        "delegatedBy", "workflow-service",
                        "delegatedAgents", delegatedAgents,
                        "recommendation", state.currentRecommendation().name(),
                        "approvalStatus", state.approvalState().approvalStatus().name(),
                        "instruction", blankSafe(command.message())))));
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

    private List<String> selectDelegates(String message) {
        String normalized = blankSafe(message).toLowerCase();
        if (containsAny(normalized, "shipment", "tracking", "carrier", "delivery")) {
            return List.of("shipment-agent");
        }
        if (containsAny(normalized, "escrow", "refund", "hold", "settlement", "fund")) {
            return List.of("escrow-agent");
        }
        if (containsAny(normalized, "risk", "fraud", "policy", "manual review")) {
            return List.of("risk-agent");
        }
        return List.of("shipment-agent", "risk-agent");
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private String delegateFocus(String delegate) {
        return switch (delegate) {
            case "shipment-agent" -> "shipment evidence";
            case "escrow-agent" -> "escrow and settlement posture";
            case "risk-agent" -> "risk controls and policy flags";
            default -> "workflow evidence";
        };
    }

    private String completionMessage(Recommendation recommendation, List<String> delegates) {
        return "Workflow service delegated the operator follow-up to " + String.join(", ", delegates)
                + " and kept the case on the " + recommendation.name().toLowerCase().replace('_', ' ') + " path.";
    }

    private WorkflowActivity activity(OffsetDateTime timestamp, String kind, String source, String message) {
        return activity(timestamp, kind, source, message, Map.of());
    }

    private WorkflowActivity activity(OffsetDateTime timestamp, String kind, String source, String message, Map<String, Object> payload) {
        return new WorkflowActivity(kind, source, message, writePayload(payload), timestamp);
    }

    private String blankSafe(String value) {
        return value == null ? "" : value;
    }

    private String writePayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize workflow activity payload", exception);
        }
    }
}