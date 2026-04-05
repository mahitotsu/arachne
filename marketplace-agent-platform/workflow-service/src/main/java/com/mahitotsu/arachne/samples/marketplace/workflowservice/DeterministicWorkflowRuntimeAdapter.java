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
        ScenarioDrivenRecommendation.Assessment assessment = ScenarioDrivenRecommendation.assess(
                command.caseType(),
                command.amount(),
                rawEvidence);
        Recommendation recommendation = assessment.recommendation();
        EvidenceView evidence = new EvidenceView(
                rawEvidence.shipment().milestoneSummary() + " Tracking number: " + rawEvidence.shipment().trackingNumber() + ". "
                        + rawEvidence.shipment().shippingExceptionSummary(),
                rawEvidence.escrow().summary(),
                rawEvidence.risk().summary(),
                POLICY_REFERENCE);
        java.util.ArrayList<WorkflowActivity> activities = new java.util.ArrayList<>(List.of(
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
                        assessment.message(),
                        Map.of(
                                "type", "workflow_decision",
                                "recommendation", recommendation.name(),
                                "policyReference", POLICY_REFERENCE))));
        if (assessment.approvalRequired()) {
            activities.add(activity(
                    now.plusSeconds(3),
                    "APPROVAL_REQUESTED",
                    "workflow-service",
                    "Finance control approval is required for settlement progression.",
                    Map.of(
                            "type", "approval_gate",
                            "requestedRole", "FINANCE_CONTROL",
                            "recommendation", recommendation.name())));
        }
        return new StartAssessment(recommendation, evidence, List.copyOf(activities));
    }

    @Override
    public FollowUpAssessment continueWorkflow(WorkflowSessionState state, ContinueWorkflowCommand command, OffsetDateTime now) {
        boolean resolutionGuidanceRequest = isResolutionGuidanceRequest(command.message());
        List<String> delegatedAgents = selectDelegates(state, command.message(), resolutionGuidanceRequest);
        LinkedHashMap<String, Object> instructionPayload = new LinkedHashMap<>();
        instructionPayload.put("type", "operator_instruction");
        instructionPayload.put("instruction", blankSafe(command.message()));
        instructionPayload.put("operatorId", blankSafe(command.operatorId()));
        instructionPayload.put("operatorRole", blankSafe(command.operatorRole()));
        instructionPayload.put("delegatedAgents", delegatedAgents);
        instructionPayload.put("recommendation", state.currentRecommendation().name());

        List<WorkflowActivity> activities = new java.util.ArrayList<>();
        activities.add(activity(
                now,
                "OPERATOR_INSTRUCTION_RECEIVED",
                "workflow-service",
                delegatedAgents.isEmpty()
                        ? "Workflow service accepted the operator instruction and prepared a workflow answer from the current case state."
                        : "Workflow service accepted the operator instruction and routed it to the relevant domain reviewers.",
                instructionPayload));

        java.util.ArrayList<String> delegateSummaries = new java.util.ArrayList<>();
        int activityOffset = 1;
        for (String delegatedAgent : delegatedAgents) {
            String focusSummary = evidenceForDelegate(state, delegatedAgent);
            delegateSummaries.add(focusSummary);
            activities.add(activity(
                    now.plusSeconds(activityOffset++),
                    "AGENT_RESPONSE",
                    delegatedAgent,
                    "Deterministic follow-up review focused on the requested evidence slice.",
                    Map.of(
                            "type", "agent_response",
                            "agent", delegatedAgent,
                            "focus", delegateFocus(delegatedAgent),
                            "summary", focusSummary,
                            "instruction", blankSafe(command.message()))));
        }

        activities.add(activity(
                now.plusSeconds(activityOffset),
                "OPERATOR_REQUEST_COMPLETED",
                "workflow-service",
                completionMessage(state, command.message(), delegatedAgents, delegateSummaries),
                Map.of(
                        "type", "workflow_completion",
                        "delegatedBy", "workflow-service",
                        "delegatedAgents", delegatedAgents,
                        "recommendation", state.currentRecommendation().name(),
                        "approvalStatus", state.approvalState().approvalStatus().name(),
                        "instruction", blankSafe(command.message()),
                        "delegateSummaries", delegateSummaries)));

        return new FollowUpAssessment(state.currentRecommendation(), activities);
    }

    private List<String> selectDelegates(WorkflowSessionState state, String message, boolean resolutionGuidanceRequest) {
        if (resolutionGuidanceRequest) {
            return resolutionGuidanceTargets(state);
        }
        return selectDelegates(message);
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

    private List<String> resolutionGuidanceTargets(WorkflowSessionState state) {
        if (state.workflowStatus() == WorkflowContracts.WorkflowStatus.COMPLETED && state.outcome() != null) {
            return List.of();
        }
        if (state.workflowStatus() == WorkflowContracts.WorkflowStatus.READY_FOR_SETTLEMENT) {
            return List.of("escrow-agent", "risk-agent");
        }
        if (state.currentRecommendation() == Recommendation.PENDING_MORE_EVIDENCE) {
            return List.of("shipment-agent", "risk-agent");
        }
        return List.of("shipment-agent", "escrow-agent", "risk-agent");
    }

    private boolean isResolutionGuidanceRequest(String message) {
        String normalized = blankSafe(message).toLowerCase();
        String compact = normalized.replaceAll("\\s+", "");
        return containsAny(normalized,
                "resolve",
                "resolution",
                "what next",
                "next step",
                "what should",
                "how to resolve",
                "how can this be solved",
                "how can it be solved")
                || containsAny(compact, "どうすれば", "どうしたら", "どうやって", "解決", "対応", "次に", "何をすれば");
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

    private String evidenceForDelegate(WorkflowSessionState state, String delegate) {
        return switch (delegate) {
            case "shipment-agent" -> state.evidence().shipmentEvidence();
            case "escrow-agent" -> state.evidence().escrowEvidence();
            case "risk-agent" -> state.evidence().riskEvidence();
            default -> state.evidence().policyReference();
        };
    }

    private String completionMessage(
            WorkflowSessionState state,
            String operatorMessage,
            List<String> delegates,
            List<String> delegateSummaries) {
        if (isResolutionGuidanceRequest(operatorMessage)) {
            return resolutionGuidanceMessage(state, delegates, delegateSummaries);
        }
        Recommendation recommendation = state.currentRecommendation();
        return "Workflow service delegated the operator follow-up to " + String.join(", ", delegates)
                + " and kept the case on the " + recommendation.name().toLowerCase().replace('_', ' ') + " path.";
    }

    private String resolutionGuidanceMessage(
            WorkflowSessionState state,
            List<String> delegates,
            List<String> delegateSummaries) {
        if (state.outcome() != null && state.workflowStatus() == WorkflowContracts.WorkflowStatus.COMPLETED) {
            return "This case is already resolved. "
                    + state.outcome().summary()
                    + " No further workflow action is required unless new evidence is introduced.";
        }
        if (!delegates.isEmpty()) {
            return "After consulting " + String.join(", ", delegates) + ", "
                    + nextStepGuidance(state) + " "
                    + aggregateDelegateInsights(delegates, delegateSummaries);
        }
        return nextStepGuidance(state);
    }

    private String nextStepGuidance(WorkflowSessionState state) {
        if (state.approvalState().approvalStatus() == WorkflowContracts.ApprovalStatus.PENDING_FINANCE_CONTROL) {
            return "The next step to resolve this case is finance control approval. The workflow has already gathered shipment, escrow, and risk evidence, and settlement cannot proceed until finance control responds.";
        }
        if (state.approvalState().approvalStatus() == WorkflowContracts.ApprovalStatus.REJECTED) {
            return "This case cannot be resolved yet because finance control rejected the previous recommendation. Gather more evidence before asking for another approval decision.";
        }
        if (state.currentRecommendation() == Recommendation.PENDING_MORE_EVIDENCE) {
            return "The next step is to gather more evidence before the workflow can recommend a settlement path.";
        }
        return "The workflow currently recommends "
                + state.currentRecommendation().name().toLowerCase().replace('_', ' ')
                + ", and the next step depends on the current approval state of "
                + state.approvalState().approvalStatus().name().toLowerCase().replace('_', ' ')
                + ".";
    }

    private String aggregateDelegateInsights(List<String> delegates, List<String> delegateSummaries) {
        java.util.ArrayList<String> insights = new java.util.ArrayList<>();
        for (int index = 0; index < Math.min(delegates.size(), delegateSummaries.size()); index++) {
            insights.add(delegates.get(index) + " reported: " + delegateSummaries.get(index));
        }
        return String.join(" ", insights);
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