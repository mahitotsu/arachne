package com.mahitotsu.arachne.samples.marketplace.workflowservice;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mahitotsu.arachne.samples.marketplace.workflowservice.WorkflowContracts.ContinueWorkflowCommand;
import com.mahitotsu.arachne.samples.marketplace.workflowservice.WorkflowContracts.EvidenceView;
import com.mahitotsu.arachne.samples.marketplace.workflowservice.WorkflowContracts.Recommendation;
import com.mahitotsu.arachne.samples.marketplace.workflowservice.WorkflowContracts.ResumeWorkflowCommand;
import com.mahitotsu.arachne.samples.marketplace.workflowservice.WorkflowContracts.StartWorkflowCommand;
import com.mahitotsu.arachne.samples.marketplace.workflowservice.WorkflowContracts.WorkflowActivity;
import com.mahitotsu.arachne.strands.agent.Agent;
import com.mahitotsu.arachne.strands.agent.AgentInterrupt;
import com.mahitotsu.arachne.strands.agent.AgentResult;
import com.mahitotsu.arachne.strands.agent.InterruptResponse;
import com.mahitotsu.arachne.strands.hooks.HookProvider;
import com.mahitotsu.arachne.strands.skills.Skill;
import com.mahitotsu.arachne.strands.spring.AgentFactory;
import com.mahitotsu.arachne.strands.tool.ToolExecutionMode;

final class ArachneWorkflowRuntimeAdapter implements WorkflowRuntimeAdapter {

    private final AgentFactory agentFactory;
    private final DownstreamGateway downstreamGateway;
    private final List<Skill> caseWorkflowAssessmentSkills;
    private final List<Skill> caseWorkflowApprovalSkills;
    private final ObjectMapper objectMapper;
    private final MarketplaceOperatorContextPlugin marketplaceOperatorContextPlugin;
    private final MarketplaceSettlementShortcutSteering marketplaceSettlementShortcutSteering;

    ArachneWorkflowRuntimeAdapter(
            AgentFactory agentFactory,
        DownstreamGateway downstreamGateway,
        List<Skill> caseWorkflowAssessmentSkills,
        List<Skill> caseWorkflowApprovalSkills,
        ObjectMapper objectMapper,
        MarketplaceOperatorContextPlugin marketplaceOperatorContextPlugin,
        MarketplaceSettlementShortcutSteering marketplaceSettlementShortcutSteering) {
        this.agentFactory = agentFactory;
    this.downstreamGateway = downstreamGateway;
        this.caseWorkflowAssessmentSkills = caseWorkflowAssessmentSkills;
        this.caseWorkflowApprovalSkills = caseWorkflowApprovalSkills;
    this.objectMapper = objectMapper;
    this.marketplaceOperatorContextPlugin = marketplaceOperatorContextPlugin;
    this.marketplaceSettlementShortcutSteering = marketplaceSettlementShortcutSteering;
    }

    @Override
    public StartAssessment assessStart(StartWorkflowCommand command, RawEvidence rawEvidence, OffsetDateTime now) {
    AgentEvidenceSummary shipment = new AgentEvidenceSummary(shipmentSummary(rawEvidence.shipment()));
    AgentEvidenceSummary escrow = new AgentEvidenceSummary(escrowSummary(rawEvidence.escrow()));
    AgentEvidenceSummary risk = new AgentEvidenceSummary(riskSummary(rawEvidence.risk()));
        List<WorkflowActivity> workflowProgressActivities = new ArrayList<>();
    WorkflowDecision decision = agentFactory.builder("case-workflow-agent")
                .skills(caseWorkflowAssessmentSkills)
                .plugins(marketplaceOperatorContextPlugin)
                .steeringHandlers(marketplaceSettlementShortcutSteering)
                .toolExecutionMode(ToolExecutionMode.PARALLEL)
                .hooks(workflowProgressHook(workflowProgressActivities, now.plusSeconds(4)))
                .build()
                .run(workflowPrompt(command, shipment, escrow, risk), WorkflowDecision.class);
        List<WorkflowActivity> activities = new ArrayList<>();
        activities.add(activity(
            now,
            "DELEGATION_STARTED",
            "case-workflow-agent",
            "case-workflow-agent activated dispute-intake and investigation skills for the new case.",
            Map.of(
                "type", "delegation_start",
                "phase", "start",
                "delegatedBy", "case-workflow-agent",
                "delegatedAgents", List.of("shipment-agent", "escrow-agent", "risk-agent"),
                "instruction", blankSafe(command.initialMessage()))));
        activities.add(activity(
            now.plusSeconds(1),
            "EVIDENCE_RECEIVED",
            "shipment-agent",
            shipment.summary(),
            Map.of(
                "type", "agent_response",
                "agent", "shipment-agent",
                "focus", "shipment evidence",
                "summary", shipment.summary())));
        activities.add(activity(
            now.plusSeconds(2),
            "EVIDENCE_RECEIVED",
            "escrow-agent",
            escrow.summary(),
            Map.of(
                "type", "agent_response",
                "agent", "escrow-agent",
                "focus", "escrow and settlement posture",
                "summary", escrow.summary())));
        activities.add(activity(
            now.plusSeconds(3),
            "EVIDENCE_RECEIVED",
            "risk-agent",
            risk.summary(),
            Map.of(
                "type", "agent_response",
                "agent", "risk-agent",
                "focus", "risk controls and policy flags",
                "summary", risk.summary())));
        activities.addAll(workflowProgressActivities);
        OffsetDateTime finalTimestamp = now.plusSeconds(4L + workflowProgressActivities.size());
        activities.add(activity(
            finalTimestamp,
            "RECOMMENDATION_UPDATED",
            "case-workflow-agent",
            decision.recommendationMessage(),
            Map.of(
                "type", "workflow_decision",
                "recommendation", decision.recommendation().name(),
                "policyReference", decision.policyReference())));
        if (!decision.approvalMessage().isBlank()) {
            activities.add(activity(
                finalTimestamp.plusSeconds(1),
                "APPROVAL_REQUESTED",
                "case-workflow-agent",
                decision.approvalMessage(),
                Map.of(
                    "type", "approval_gate",
                    "requestedRole", "FINANCE_CONTROL",
                    "recommendation", decision.recommendation().name())));
        }

        return new StartAssessment(
                decision.recommendation(),
                new EvidenceView(shipment.summary(), escrow.summary(), risk.summary(), decision.policyReference()),
                activities);
    }

    @Override
    public FollowUpAssessment continueWorkflow(WorkflowSessionState state, ContinueWorkflowCommand command, OffsetDateTime now) {
        boolean resolutionGuidanceRequest = isResolutionGuidanceRequest(command.message());
        List<DelegationTarget> selectedTargets = selectDelegates(state, command.message(), resolutionGuidanceRequest);
        List<WorkflowActivity> activities = new ArrayList<>();
        activities.add(activity(
                now,
                "OPERATOR_INSTRUCTION_RECEIVED",
                "case-workflow-agent",
                selectedTargets.isEmpty()
                        ? "case-workflow-agent accepted the operator instruction and prepared a workflow answer from the current case state."
                        : "case-workflow-agent accepted the operator instruction and started targeted delegation.",
                Map.of(
                        "type", "operator_instruction",
                        "delegatedBy", "case-workflow-agent",
                        "instruction", blankSafe(command.message()),
                        "operatorId", blankSafe(command.operatorId()),
                        "operatorRole", blankSafe(command.operatorRole()),
                        "delegatedAgents", selectedTargets.stream().map(DelegationTarget::agentName).toList(),
                        "recommendation", state.currentRecommendation().name())));

        List<String> delegateSummaries = new ArrayList<>();
        int activityOffset = 1;
        for (DelegationTarget target : selectedTargets) {
            activities.add(activity(
                    now.plusSeconds(activityOffset++),
                    "DELEGATION_ROUTED",
                    "case-workflow-agent",
                    "case-workflow-agent delegated the operator follow-up to " + target.agentName() + ".",
                    Map.of(
                            "type", "delegation_assignment",
                            "delegatedBy", "case-workflow-agent",
                            "agent", target.agentName(),
                            "focus", target.focusLabel(),
                            "instruction", blankSafe(command.message()))));

            AgentEvidenceSummary summary = runDelegate(state, command, target);
            delegateSummaries.add(summary.summary());
            activities.add(activity(
                    now.plusSeconds(activityOffset++),
                    "AGENT_RESPONSE",
                    target.agentName(),
                    summary.summary(),
                    Map.of(
                            "type", "agent_response",
                            "agent", target.agentName(),
                            "focus", target.focusLabel(),
                            "summary", summary.summary(),
                            "instruction", blankSafe(command.message()))));
        }

        activities.add(activity(
                now.plusSeconds(activityOffset),
                "OPERATOR_REQUEST_COMPLETED",
                "case-workflow-agent",
                completionMessage(state, command.message(), selectedTargets, delegateSummaries),
                Map.of(
                        "type", "workflow_completion",
                        "delegatedBy", "case-workflow-agent",
                        "delegatedAgents", selectedTargets.stream().map(DelegationTarget::agentName).toList(),
                        "instruction", blankSafe(command.message()),
                        "recommendation", state.currentRecommendation().name(),
                        "approvalStatus", state.approvalState().approvalStatus().name(),
                        "delegateSummaries", delegateSummaries)));

        return new FollowUpAssessment(state.currentRecommendation(), activities);
    }

    private List<DelegationTarget> selectDelegates(WorkflowSessionState state, String message, boolean resolutionGuidanceRequest) {
        if (resolutionGuidanceRequest) {
            return resolutionGuidanceTargets(state);
        }
        return selectDelegates(message);
    }

    @Override
    public Optional<ApprovalPause> pauseForApproval(StartWorkflowCommand command, StartAssessment assessment) {
        if (assessment.recommendation() == Recommendation.PENDING_MORE_EVIDENCE) {
            return Optional.empty();
        }
        String sessionId = approvalSessionId(command.caseId());
        List<WorkflowActivity> approvalActivities = new ArrayList<>();
        OffsetDateTime approvalTimestamp = command.requestedAt() == null
            ? OffsetDateTime.now()
            : command.requestedAt().plusSeconds(5);
        AtomicInteger approvalOffset = new AtomicInteger();
        AgentResult interrupted = approvalAgent(sessionId, toolName -> approvalActivities.add(activity(
                approvalTimestamp.plusSeconds(approvalOffset.getAndIncrement()),
                "HOOK_FORCED_TOOL_SELECTION",
                "workflow-hook",
                "approval-start hook forced finance_control_approval before the model could take another turn.",
                Map.of(
                    "type", "hook_event",
                    "hookName", "approval_start_force_finance_tool",
                    "toolName", toolName,
                    "phase", "approval-start"))),
            input -> approvalActivities.add(activity(
                approvalTimestamp.plusSeconds(approvalOffset.getAndIncrement()),
                "TOOL_CALL_RECORDED",
                "case-workflow-agent",
                "case-workflow-agent invoked finance_control_approval to request a finance-control decision.",
                approvalToolPayload(input))))
            .run(approvalPrompt(command, assessment.recommendation()));
        if (!interrupted.interrupted()) {
            throw new IllegalStateException("Expected finance control approval to interrupt the Arachne workflow.");
        }
        AgentInterrupt interrupt = interrupted.interrupts().getFirst();
        approvalActivities.add(activity(
            approvalTimestamp.plusSeconds(approvalOffset.getAndIncrement()),
            "APPROVAL_INTERRUPT_REGISTERED",
            "workflow-runtime",
            "workflow-runtime paused the case until finance control responds to the approval request.",
            Map.of(
                "type", "runtime_event",
                "interruptName", MarketplaceFinanceControlApprovalPlugin.INTERRUPT_NAME,
                "interruptId", interrupt.id(),
                "requestedRole", "FINANCE_CONTROL")));
        return Optional.of(new ApprovalPause(sessionId, interrupt.id(), approvalActivities));
    }

    @Override
    public Optional<ApprovalResume> resumeApproval(WorkflowSessionState state, ResumeWorkflowCommand command) {
        if (state.approvalRuntimeSessionId() == null || state.approvalInterruptId() == null) {
            return Optional.empty();
        }
        AgentResult resumed = approvalAgent(state.approvalRuntimeSessionId())
                .resume(new InterruptResponse(state.approvalInterruptId(), approvalResponse(command)));
        if (resumed.interrupted()) {
            throw new IllegalStateException("Finance control approval resumed into another interrupt instead of completing.");
        }
        return Optional.of(new ApprovalResume(resumed.text()));
    }

    private String workflowPrompt(
            StartWorkflowCommand command,
            AgentEvidenceSummary shipment,
            AgentEvidenceSummary escrow,
            AgentEvidenceSummary risk) {
        return String.join("\n",
                "agent=case-workflow-agent",
            "mode=start-assessment",
                "caseId=" + command.caseId(),
                "caseType=" + command.caseType(),
                "orderId=" + command.orderId(),
                "amount=" + command.amount(),
                "currency=" + command.currency(),
                "initialMessage=" + blankSafe(command.initialMessage()),
                "shipmentSummary=" + shipment.summary(),
                "escrowSummary=" + escrow.summary(),
                "riskSummary=" + risk.summary(),
                "instructions=Use each available resource tool at most once, do not repeat the same tool call with the same input, do not request finance_control_approval in this mode, and return the final structured workflow decision as soon as the packaged guidance has been reviewed.");
    }

    private HookProvider workflowProgressHook(List<WorkflowActivity> activities, OffsetDateTime startTimestamp) {
        Set<String> emittedProgressKeys = new HashSet<>();
        return registrar -> registrar
                .beforeToolCall(event -> {
                    WorkflowActivity next = toolUseActivity(event.toolName(), event.input(), startTimestamp.plusSeconds(activities.size()));
                    if (next != null && emittedProgressKeys.add(progressKey("before", event.toolName(), event.input()))) {
                        activities.add(next);
                    }
                })
                .afterToolCall(event -> {
                    WorkflowActivity next = toolResultActivity(event.toolName(), event.result(), startTimestamp.plusSeconds(activities.size()));
                    if (next != null && emittedProgressKeys.add(progressKey("after", event.toolName(), event.result().content()))) {
                        activities.add(next);
                    }
                });
    }

    private String progressKey(String phase, String toolName, Object value) {
        return phase + ":" + toolName + ":" + serializeProgressValue(value);
    }

    private String serializeProgressValue(Object value) {
        if (value == null) {
            return "null";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return String.valueOf(value);
        }
    }

    private WorkflowActivity toolUseActivity(String toolName, Object input, OffsetDateTime timestamp) {
        if ("resource_list".equals(toolName)) {
            return activity(
                    timestamp,
                    "STREAM_PROGRESS",
                    "case-workflow-agent",
                    "case-workflow-agent listed the packaged marketplace guidance before updating the recommendation.",
                    Map.of(
                            "type", "tool_call",
                            "toolName", "resource_list",
                            "location", "classpath:/marketplace-workflow/"));
        }
        if ("resource_reader".equals(toolName) && input instanceof Map<?, ?> inputMap) {
            String location = String.valueOf(inputMap.get("location"));
            if (location.endsWith("settlement-policy-summary.md")) {
                return activity(
                        timestamp,
                        "STREAM_PROGRESS",
                        "case-workflow-agent",
                        "case-workflow-agent reviewed the packaged settlement policy summary.",
                        Map.of(
                                "type", "tool_call",
                                "toolName", "resource_reader",
                                "location", location));
            }
            if (location.endsWith("finance-control-thresholds.md")) {
                return activity(
                        timestamp,
                        "STREAM_PROGRESS",
                        "case-workflow-agent",
                        "case-workflow-agent reviewed finance-control thresholds before any settlement-changing action.",
                        Map.of(
                                "type", "tool_call",
                                "toolName", "resource_reader",
                                "location", location));
            }
            if (location.endsWith("item-not-received.md")) {
                return activity(
                        timestamp,
                        "STREAM_PROGRESS",
                        "case-workflow-agent",
                        "case-workflow-agent consulted the item-not-received runbook for the active case.",
                        Map.of(
                                "type", "tool_call",
                                "toolName", "resource_reader",
                                "location", location));
            }
        }
        if (MarketplaceSettlementShortcutSteering.TOOL_NAME.equals(toolName)) {
            return activity(
                    timestamp,
                    "SETTLEMENT_SHORTCUT_ATTEMPTED",
                    "case-workflow-agent",
                    "case-workflow-agent attempted an automatic settlement shortcut on the low-value refund path.",
                    Map.of(
                            "type", "tool_call",
                            "toolName", MarketplaceSettlementShortcutSteering.TOOL_NAME,
                            "path", "instant_refund"));
        }
        return null;
    }

    private WorkflowActivity toolResultActivity(String toolName, com.mahitotsu.arachne.strands.tool.ToolResult result, OffsetDateTime timestamp) {
        if (MarketplaceSettlementShortcutSteering.TOOL_NAME.equals(toolName)
                && result.status() == com.mahitotsu.arachne.strands.tool.ToolResult.ToolStatus.ERROR
                && MarketplaceSettlementShortcutSteering.GUIDANCE.equals(String.valueOf(result.content()))) {
            return activity(
                    timestamp,
                    "STEERING_APPLIED",
                    "workflow-steering",
                    MarketplaceSettlementShortcutSteering.GUIDANCE,
                    Map.of(
                            "type", "tool_result",
                            "toolName", MarketplaceSettlementShortcutSteering.TOOL_NAME,
                            "status", result.status().name(),
                            "content", String.valueOf(result.content())));
        }
        if (MarketplaceOperatorContextPlugin.TOOL_NAME.equals(toolName)) {
            if (result.status() == com.mahitotsu.arachne.strands.tool.ToolResult.ToolStatus.SUCCESS
                    && result.content() instanceof Map<?, ?> payload) {
                return activity(
                        timestamp,
                        "CONTEXT_PROPAGATED",
                        "workflow-security",
                        "workflow-service propagated operator authorization context "
                                + payload.get("operatorId")
                                + "/"
                                + payload.get("operatorRole")
                                + " into parallel tool execution for "
                                + payload.get("probe")
                                + ".",
                        Map.of(
                                "type", "tool_result",
                                "toolName", MarketplaceOperatorContextPlugin.TOOL_NAME,
                                "status", result.status().name(),
                                "probe", String.valueOf(payload.get("probe")),
                                "operatorId", String.valueOf(payload.get("operatorId")),
                                "operatorRole", String.valueOf(payload.get("operatorRole"))));
            }
            return activity(
                    timestamp,
                    "CONTEXT_PROPAGATION_FAILED",
                    "workflow-security",
                    "workflow-service failed to propagate operator authorization context into parallel tool execution: "
                            + result.content(),
                    Map.of(
                            "type", "tool_result",
                            "toolName", MarketplaceOperatorContextPlugin.TOOL_NAME,
                            "status", result.status().name(),
                            "content", String.valueOf(result.content())));
        }
        return null;
    }

    private AgentEvidenceSummary runDelegate(WorkflowSessionState state, ContinueWorkflowCommand command, DelegationTarget target) {
        return switch (target.agentName()) {
            case "shipment-agent" -> new AgentEvidenceSummary(downstreamGateway.shipmentSpecialistReview(
                new DownstreamContracts.ShipmentSpecialistReviewRequest(
                    state.caseId(),
                    state.caseType(),
                    command.message(),
                    state.orderId(),
                    command.message()))
                .summary());
            case "escrow-agent" -> new AgentEvidenceSummary(downstreamGateway.escrowSpecialistReview(
                new DownstreamContracts.EscrowSpecialistReviewRequest(
                    state.caseId(),
                    state.caseType(),
                    state.orderId(),
                    command.message(),
                    state.amount(),
                    state.currency(),
                    command.operatorId(),
                    command.operatorRole(),
                    command.message()))
                .summary());
            case "risk-agent" -> new AgentEvidenceSummary(downstreamGateway.riskSpecialistReview(
                new DownstreamContracts.RiskSpecialistReviewRequest(
                    state.caseId(),
                    state.caseType(),
                    state.orderId(),
                    command.message(),
                    command.operatorRole(),
                    command.message()))
                .summary());
            default -> throw new IllegalArgumentException("Unsupported delegation target: " + target.agentName());
        };
    }

    private List<DelegationTarget> selectDelegates(String message) {
        String normalized = blankSafe(message).toLowerCase();
        String compact = normalized.replaceAll("\\s+", "");
        if (containsAny(normalized, "shipment", "tracking", "carrier", "delivery")) {
            return List.of(new DelegationTarget("shipment-agent", "shipment evidence", "shipment"));
        }
        if (containsAny(normalized, "escrow", "refund", "hold", "settlement", "fund")) {
            return List.of(new DelegationTarget("escrow-agent", "escrow and settlement posture", "escrow"));
        }
        if (containsAny(normalized, "risk", "fraud", "policy", "manual review")) {
            return List.of(new DelegationTarget("risk-agent", "risk controls and policy flags", "risk"));
        }
        if (containsAny(normalized,
                "resolve",
                "resolution",
                "what next",
                "next step",
                "what should",
                "how to",
                "how do")
                || containsAny(compact, "どうすれば", "どうしたら", "どうやって", "解決", "対応", "次に", "何をすれば")) {
            return List.of(
                    new DelegationTarget("shipment-agent", "shipment evidence", "shipment"),
                    new DelegationTarget("escrow-agent", "escrow and settlement posture", "escrow"),
                    new DelegationTarget("risk-agent", "risk controls and policy flags", "risk"));
        }
        return List.of(
                new DelegationTarget("shipment-agent", "shipment evidence", "shipment"),
                new DelegationTarget("risk-agent", "risk controls and policy flags", "risk"));
    }

    private List<DelegationTarget> resolutionGuidanceTargets(WorkflowSessionState state) {
        if (state.workflowStatus() == WorkflowContracts.WorkflowStatus.COMPLETED && state.outcome() != null) {
            return List.of();
        }
        if (state.workflowStatus() == WorkflowContracts.WorkflowStatus.READY_FOR_SETTLEMENT) {
            return List.of(
                    new DelegationTarget("escrow-agent", "escrow and settlement posture", "escrow"),
                    new DelegationTarget("risk-agent", "risk controls and policy flags", "risk"));
        }
        return List.of(
                new DelegationTarget("shipment-agent", "shipment evidence", "shipment"),
                new DelegationTarget("escrow-agent", "escrow and settlement posture", "escrow"),
                new DelegationTarget("risk-agent", "risk controls and policy flags", "risk"));
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

    private String shipmentSummary(DownstreamContracts.ShipmentEvidenceSummary shipment) {
        return shipment.milestoneSummary() + " Tracking number: " + shipment.trackingNumber() + ". " + shipment.shippingExceptionSummary();
    }

    private String escrowSummary(DownstreamContracts.EscrowEvidenceSummary escrow) {
        return escrow.summary() + " Hold state: " + escrow.holdState() + ". Eligibility: " + escrow.settlementEligibility() + ".";
    }

    private String riskSummary(DownstreamContracts.RiskReviewSummary risk) {
        return risk.summary() + " Indicators: " + risk.indicatorSummary() + ". Flags: " + String.join(",", risk.policyFlags()) + ".";
    }

    private String completionMessage(
            WorkflowSessionState state,
            String operatorMessage,
            List<DelegationTarget> targets,
            List<String> delegateSummaries) {
        if (isResolutionGuidanceRequest(operatorMessage)) {
            return resolutionGuidanceMessage(state, targets, delegateSummaries);
        }
        Recommendation recommendation = state.currentRecommendation();
        String approvalStatus = state.approvalState().approvalStatus().name();
        return "case-workflow-agent delegated the operator instruction to "
                + String.join(", ", targets.stream().map(DelegationTarget::agentName).toList())
                + " and kept the case on the "
                + recommendation.name().toLowerCase().replace('_', ' ')
                + " path while approval remains "
                + approvalStatus.toLowerCase().replace('_', ' ')
                + ".";
    }

    private String resolutionGuidanceMessage(
            WorkflowSessionState state,
            List<DelegationTarget> targets,
            List<String> delegateSummaries) {
        if (state.outcome() != null && state.workflowStatus() == WorkflowContracts.WorkflowStatus.COMPLETED) {
            return "This case is already resolved. "
                    + state.outcome().summary()
                    + " No further workflow action is required unless new evidence is introduced.";
        }
        if (!targets.isEmpty()) {
            return "After consulting "
                    + String.join(", ", targets.stream().map(DelegationTarget::agentName).toList())
                    + ", "
                    + nextStepGuidance(state)
                    + " "
                    + aggregateDelegateInsights(targets, delegateSummaries);
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

    private String aggregateDelegateInsights(List<DelegationTarget> targets, List<String> delegateSummaries) {
        if (delegateSummaries.isEmpty()) {
            return "";
        }
        List<String> insights = new ArrayList<>();
        for (int index = 0; index < Math.min(targets.size(), delegateSummaries.size()); index++) {
            insights.add(targets.get(index).agentName() + " reported: " + delegateSummaries.get(index));
        }
        return String.join(" ", insights);
    }

    private Agent approvalAgent(String sessionId, java.util.function.Consumer<String> forcedToolRecorder, java.util.function.Consumer<Object> toolInputRecorder) {
        return agentFactory.builder("case-workflow-agent")
            .skills(caseWorkflowApprovalSkills)
                .plugins(new MarketplaceFinanceControlApprovalPlugin(toolInputRecorder))
                .hooks(new MarketplaceApprovalInterruptHookProvider(forcedToolRecorder))
                .sessionId(sessionId)
                .build();
    }

    private Agent approvalAgent(String sessionId) {
        return approvalAgent(sessionId, toolName -> {
        }, input -> {
        });
    }

    private String approvalPrompt(StartWorkflowCommand command, Recommendation recommendation) {
        return String.join("\n",
                "mode=approval-start",
                "caseId=" + command.caseId(),
                "caseType=" + command.caseType(),
                "orderId=" + command.orderId(),
                "recommendation=" + recommendation.name(),
                "approvalMessage=Finance control approval is required for settlement progression.");
    }

    private Map<String, Object> approvalResponse(ResumeWorkflowCommand command) {
        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("decision", blankSafe(command.decision()));
        response.put("comment", blankSafe(command.comment()));
        response.put("actorId", blankSafe(command.actorId()));
        response.put("actorRole", blankSafe(command.actorRole()));
        response.put("requestedAt", command.requestedAt() == null ? "" : command.requestedAt().toString());
        return response;
    }

    private String approvalSessionId(String caseId) {
        return "marketplace-approval:" + caseId;
    }

    private Map<String, Object> approvalToolPayload(Object input) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "tool_call");
        payload.put("toolName", MarketplaceFinanceControlApprovalPlugin.TOOL_NAME);
        payload.put("requestedRole", "FINANCE_CONTROL");
        if (input instanceof Map<?, ?> inputMap) {
            inputMap.forEach((key, value) -> payload.put(String.valueOf(key), value));
        }
        return payload;
    }

    private String blankSafe(String value) {
        return value == null ? "" : value;
    }

    private WorkflowActivity activity(OffsetDateTime timestamp, String kind, String source, String message, Map<String, Object> payload) {
        return new WorkflowActivity(kind, source, message, writePayload(payload), timestamp);
    }

    private String writePayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize workflow activity payload", exception);
        }
    }

    record AgentEvidenceSummary(String summary) {
    }

    record WorkflowDecision(
            Recommendation recommendation,
            String recommendationMessage,
            String approvalMessage,
            String policyReference) {
    }

    record DelegationTarget(String agentName, String focusLabel, String focusCode) {
    }
}