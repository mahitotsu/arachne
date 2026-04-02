package com.mahitotsu.arachne.samples.marketplace.workflowservice;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;

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

final class ArachneWorkflowRuntimeAdapter implements WorkflowRuntimeAdapter {

    private final AgentFactory agentFactory;
    private final List<Skill> caseWorkflowSkills;
    private final List<Skill> shipmentSkills;
    private final List<Skill> escrowSkills;
    private final List<Skill> riskSkills;
        private final MarketplaceFinanceControlApprovalPlugin marketplaceFinanceControlApprovalPlugin;
        private final MarketplaceSettlementShortcutSteering marketplaceSettlementShortcutSteering;

    ArachneWorkflowRuntimeAdapter(
            AgentFactory agentFactory,
            @Qualifier("caseWorkflowAgentSkills") List<Skill> caseWorkflowSkills,
            @Qualifier("shipmentAgentSkills") List<Skill> shipmentSkills,
            @Qualifier("escrowAgentSkills") List<Skill> escrowSkills,
                        @Qualifier("riskAgentSkills") List<Skill> riskSkills,
                        MarketplaceFinanceControlApprovalPlugin marketplaceFinanceControlApprovalPlugin,
                        MarketplaceSettlementShortcutSteering marketplaceSettlementShortcutSteering) {
        this.agentFactory = agentFactory;
        this.caseWorkflowSkills = caseWorkflowSkills;
        this.shipmentSkills = shipmentSkills;
        this.escrowSkills = escrowSkills;
        this.riskSkills = riskSkills;
                this.marketplaceFinanceControlApprovalPlugin = marketplaceFinanceControlApprovalPlugin;
                this.marketplaceSettlementShortcutSteering = marketplaceSettlementShortcutSteering;
    }

    @Override
    public StartAssessment assessStart(StartWorkflowCommand command, RawEvidence rawEvidence, OffsetDateTime now) {
        AgentEvidenceSummary shipment = agentFactory.builder("shipment-agent")
                .skills(shipmentSkills)
                .build()
                .run(shipmentPrompt(command, rawEvidence), AgentEvidenceSummary.class);
        AgentEvidenceSummary escrow = agentFactory.builder("escrow-agent")
                .skills(escrowSkills)
                .build()
                .run(escrowPrompt(command, rawEvidence), AgentEvidenceSummary.class);
        AgentEvidenceSummary risk = agentFactory.builder("risk-agent")
                .skills(riskSkills)
                .build()
                .run(riskPrompt(command, rawEvidence), AgentEvidenceSummary.class);
        List<WorkflowActivity> workflowProgressActivities = new ArrayList<>();
        WorkflowDecision decision = agentFactory.builder("case-workflow-agent")
                .skills(caseWorkflowSkills)
                .steeringHandlers(marketplaceSettlementShortcutSteering)
                .hooks(workflowProgressHook(workflowProgressActivities, now.plusSeconds(4)))
                .build()
                .run(workflowPrompt(command, shipment, escrow, risk), WorkflowDecision.class);
        List<WorkflowActivity> activities = new ArrayList<>();
        activities.add(activity(now, "DELEGATION_STARTED", "case-workflow-agent", "case-workflow-agent activated dispute-intake and investigation skills for the new case."));
        activities.add(activity(now.plusSeconds(1), "EVIDENCE_RECEIVED", "shipment-agent", shipment.summary()));
        activities.add(activity(now.plusSeconds(2), "EVIDENCE_RECEIVED", "escrow-agent", escrow.summary()));
        activities.add(activity(now.plusSeconds(3), "EVIDENCE_RECEIVED", "risk-agent", risk.summary()));
        activities.addAll(workflowProgressActivities);
        OffsetDateTime finalTimestamp = now.plusSeconds(4L + workflowProgressActivities.size());
        activities.add(activity(finalTimestamp, "RECOMMENDATION_UPDATED", "case-workflow-agent", decision.recommendationMessage()));
        activities.add(activity(finalTimestamp.plusSeconds(1), "APPROVAL_REQUESTED", "case-workflow-agent", decision.approvalMessage()));

        return new StartAssessment(
                decision.recommendation(),
                new EvidenceView(shipment.summary(), escrow.summary(), risk.summary(), decision.policyReference()),
                activities);
    }

    @Override
    public Optional<ApprovalPause> pauseForApproval(StartWorkflowCommand command, StartAssessment assessment) {
        String sessionId = approvalSessionId(command.caseId());
        AgentResult interrupted = approvalAgent(sessionId).run(approvalPrompt(command, assessment.recommendation()));
        if (!interrupted.interrupted()) {
            throw new IllegalStateException("Expected finance control approval to interrupt the Arachne workflow.");
        }
        AgentInterrupt interrupt = interrupted.interrupts().getFirst();
        return Optional.of(new ApprovalPause(sessionId, interrupt.id()));
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

    private String shipmentPrompt(StartWorkflowCommand command, RawEvidence rawEvidence) {
        return String.join("\n",
                "agent=shipment-agent",
                "caseId=" + command.caseId(),
                "caseType=" + command.caseType(),
                "orderId=" + command.orderId(),
                "trackingNumber=" + rawEvidence.shipment().trackingNumber(),
                "milestoneSummary=" + rawEvidence.shipment().milestoneSummary(),
                "shippingExceptionSummary=" + rawEvidence.shipment().shippingExceptionSummary(),
                "deliveryConfidence=" + rawEvidence.shipment().deliveryConfidence());
    }

    private String escrowPrompt(StartWorkflowCommand command, RawEvidence rawEvidence) {
        return String.join("\n",
                "agent=escrow-agent",
                "caseId=" + command.caseId(),
                "caseType=" + command.caseType(),
                "orderId=" + command.orderId(),
                "amount=" + command.amount(),
                "currency=" + command.currency(),
                "holdState=" + rawEvidence.escrow().holdState(),
                "settlementEligibility=" + rawEvidence.escrow().settlementEligibility(),
                "priorSettlementStatus=" + rawEvidence.escrow().priorSettlementStatus(),
                "summary=" + rawEvidence.escrow().summary());
    }

    private String riskPrompt(StartWorkflowCommand command, RawEvidence rawEvidence) {
        return String.join("\n",
                "agent=risk-agent",
                "caseId=" + command.caseId(),
                "caseType=" + command.caseType(),
                "orderId=" + command.orderId(),
                "manualReviewRequired=" + rawEvidence.risk().manualReviewRequired(),
                "policyFlags=" + String.join(",", rawEvidence.risk().policyFlags()),
                "indicatorSummary=" + rawEvidence.risk().indicatorSummary(),
                "summary=" + rawEvidence.risk().summary());
    }

    private String workflowPrompt(
            StartWorkflowCommand command,
            AgentEvidenceSummary shipment,
            AgentEvidenceSummary escrow,
            AgentEvidenceSummary risk) {
        return String.join("\n",
                "agent=case-workflow-agent",
                "caseId=" + command.caseId(),
                "caseType=" + command.caseType(),
                "orderId=" + command.orderId(),
                "amount=" + command.amount(),
                "currency=" + command.currency(),
                "initialMessage=" + blankSafe(command.initialMessage()),
                "shipmentSummary=" + shipment.summary(),
                "escrowSummary=" + escrow.summary(),
                "riskSummary=" + risk.summary());
    }

        private HookProvider workflowProgressHook(List<WorkflowActivity> activities, OffsetDateTime startTimestamp) {
                return registrar -> registrar
                                .beforeToolCall(event -> {
                                        WorkflowActivity next = toolUseActivity(event.toolName(), event.input(), startTimestamp.plusSeconds(activities.size()));
                                        if (next != null) {
                                                activities.add(next);
                                        }
                                })
                                .afterToolCall(event -> {
                                        WorkflowActivity next = toolResultActivity(event.toolName(), event.result(), startTimestamp.plusSeconds(activities.size()));
                                        if (next != null) {
                                                activities.add(next);
                                        }
                                });
        }

        private WorkflowActivity toolUseActivity(String toolName, Object input, OffsetDateTime timestamp) {
                if ("resource_list".equals(toolName)) {
                        return activity(timestamp, "STREAM_PROGRESS", "case-workflow-agent", "case-workflow-agent listed the packaged marketplace guidance before updating the recommendation.");
                }
                if ("resource_reader".equals(toolName) && input instanceof Map<?, ?> inputMap) {
                        String location = String.valueOf(inputMap.get("location"));
                        if (location.endsWith("settlement-policy-summary.md")) {
                                return activity(timestamp, "STREAM_PROGRESS", "case-workflow-agent", "case-workflow-agent reviewed the packaged settlement policy summary.");
                        }
                        if (location.endsWith("finance-control-thresholds.md")) {
                                return activity(timestamp, "STREAM_PROGRESS", "case-workflow-agent", "case-workflow-agent reviewed finance-control thresholds before any settlement-changing action.");
                        }
                        if (location.endsWith("item-not-received.md")) {
                                return activity(timestamp, "STREAM_PROGRESS", "case-workflow-agent", "case-workflow-agent consulted the item-not-received runbook for the active case.");
                        }
                }
                if (MarketplaceSettlementShortcutSteering.TOOL_NAME.equals(toolName)) {
                        return activity(timestamp, "SETTLEMENT_SHORTCUT_ATTEMPTED", "case-workflow-agent", "case-workflow-agent attempted an automatic settlement shortcut on the low-value refund path.");
                }
                return null;
        }

        private WorkflowActivity toolResultActivity(String toolName, com.mahitotsu.arachne.strands.tool.ToolResult result, OffsetDateTime timestamp) {
                if (MarketplaceSettlementShortcutSteering.TOOL_NAME.equals(toolName)
                                && result.status() == com.mahitotsu.arachne.strands.tool.ToolResult.ToolStatus.ERROR
                                && MarketplaceSettlementShortcutSteering.GUIDANCE.equals(String.valueOf(result.content()))) {
                        return activity(timestamp, "STEERING_APPLIED", "workflow-steering", MarketplaceSettlementShortcutSteering.GUIDANCE);
                }
                return null;
        }

        private Agent approvalAgent(String sessionId) {
                return agentFactory.builder("case-workflow-agent")
                                .skills(caseWorkflowSkills)
                                .plugins(marketplaceFinanceControlApprovalPlugin)
                                .sessionId(sessionId)
                                .build();
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

    private String blankSafe(String value) {
        return value == null ? "" : value;
    }

    private WorkflowActivity activity(OffsetDateTime timestamp, String kind, String source, String message) {
        return new WorkflowActivity(kind, source, message, "{}", timestamp);
    }

    record AgentEvidenceSummary(String summary) {
    }

    record WorkflowDecision(
            Recommendation recommendation,
            String recommendationMessage,
            String approvalMessage,
            String policyReference) {
    }
}