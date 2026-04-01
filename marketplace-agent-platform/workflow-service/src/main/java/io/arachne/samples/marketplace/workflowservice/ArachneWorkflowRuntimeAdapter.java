package io.arachne.samples.marketplace.workflowservice;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;

import io.arachne.samples.marketplace.workflowservice.WorkflowContracts.EvidenceView;
import io.arachne.samples.marketplace.workflowservice.WorkflowContracts.Recommendation;
import io.arachne.samples.marketplace.workflowservice.WorkflowContracts.StartWorkflowCommand;
import io.arachne.samples.marketplace.workflowservice.WorkflowContracts.WorkflowActivity;
import io.arachne.strands.skills.Skill;
import io.arachne.strands.spring.AgentFactory;

final class ArachneWorkflowRuntimeAdapter implements WorkflowRuntimeAdapter {

    private final AgentFactory agentFactory;
    private final List<Skill> caseWorkflowSkills;
    private final List<Skill> shipmentSkills;
    private final List<Skill> escrowSkills;
    private final List<Skill> riskSkills;

    ArachneWorkflowRuntimeAdapter(
            AgentFactory agentFactory,
            @Qualifier("caseWorkflowAgentSkills") List<Skill> caseWorkflowSkills,
            @Qualifier("shipmentAgentSkills") List<Skill> shipmentSkills,
            @Qualifier("escrowAgentSkills") List<Skill> escrowSkills,
            @Qualifier("riskAgentSkills") List<Skill> riskSkills) {
        this.agentFactory = agentFactory;
        this.caseWorkflowSkills = caseWorkflowSkills;
        this.shipmentSkills = shipmentSkills;
        this.escrowSkills = escrowSkills;
        this.riskSkills = riskSkills;
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
        WorkflowDecision decision = agentFactory.builder("case-workflow-agent")
                .skills(caseWorkflowSkills)
                .build()
                .run(workflowPrompt(command, shipment, escrow, risk), WorkflowDecision.class);

        return new StartAssessment(
                decision.recommendation(),
                new EvidenceView(shipment.summary(), escrow.summary(), risk.summary(), decision.policyReference()),
                List.of(
                        activity(now, "DELEGATION_STARTED", "case-workflow-agent", "case-workflow-agent activated dispute-intake and investigation skills for the new case."),
                        activity(now.plusSeconds(1), "EVIDENCE_RECEIVED", "shipment-agent", shipment.summary()),
                        activity(now.plusSeconds(2), "EVIDENCE_RECEIVED", "escrow-agent", escrow.summary()),
                        activity(now.plusSeconds(3), "EVIDENCE_RECEIVED", "risk-agent", risk.summary()),
                        activity(now.plusSeconds(4), "RECOMMENDATION_UPDATED", "case-workflow-agent", decision.recommendationMessage()),
                        activity(now.plusSeconds(5), "APPROVAL_REQUESTED", "case-workflow-agent", decision.approvalMessage())));
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