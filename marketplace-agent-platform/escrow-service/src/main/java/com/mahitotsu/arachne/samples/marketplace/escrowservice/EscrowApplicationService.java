package com.mahitotsu.arachne.samples.marketplace.escrowservice;

import java.time.Clock;
import java.time.OffsetDateTime;

import com.mahitotsu.arachne.strands.spring.AgentFactory;
import com.mahitotsu.arachne.strands.tool.Tool;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
class EscrowApplicationService {

    private final Clock clock;
    private final EscrowRepository repository;
    private final AgentFactory agentFactory;
    private final Tool escrowCaseLookupTool;

    EscrowApplicationService(Clock clock, EscrowRepository repository, AgentFactory agentFactory, Tool escrowCaseLookupTool) {
        this.clock = clock;
        this.repository = repository;
        this.agentFactory = agentFactory;
        this.escrowCaseLookupTool = escrowCaseLookupTool;
    }

    EscrowContracts.EscrowEvidenceSummary evidenceSummary(EscrowContracts.EscrowEvidenceRequest request) {
        repository.ensureCaseRecord(request.caseId(), request.amount(), request.currency());
        return agentFactory.builder("escrow-agent")
                .tools(escrowCaseLookupTool)
                .build()
                .run(evidencePrompt(request), EscrowContracts.EscrowEvidenceSummary.class);
    }

    EscrowContracts.EscrowSpecialistReview specialistReview(EscrowContracts.EscrowSpecialistReviewRequest request) {
        repository.ensureCaseRecord(request.caseId(), request.amount(), request.currency());
        return agentFactory.builder("escrow-agent")
                .tools(escrowCaseLookupTool)
                .build()
                .run(reviewPrompt(request), EscrowContracts.EscrowSpecialistReview.class);
    }

    EscrowContracts.SettlementOutcome executeSettlement(EscrowContracts.ExecuteSettlementCommand command) {
        if (!"FINANCE_CONTROL".equalsIgnoreCase(command.actorRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "FINANCE_CONTROL role required for settlement actions");
        }
        repository.ensureCaseRecord(command.caseId(), command.amount(), command.currency());
        var now = OffsetDateTime.now(clock);
        if ("REFUND".equalsIgnoreCase(command.action())) {
            var outcome = new EscrowContracts.SettlementOutcome(
                    "REFUND_EXECUTED",
                    "SUCCEEDED",
                    now,
                    "refund-" + command.caseId(),
                    "Escrow executed a refund after finance control approval.");
            repository.recordSettlement(command.caseId(), "REFUNDED", outcome);
            return outcome;
        }
        var outcome = new EscrowContracts.SettlementOutcome(
                "CONTINUED_HOLD_RECORDED",
                "SUCCEEDED",
                now,
                "hold-" + command.caseId(),
                "Escrow recorded the continued hold after finance control approval.");
        repository.recordSettlement(command.caseId(), "HELD", outcome);
        return outcome;
    }

    String settlementEligibility(String holdState, String caseType) {
        if ("DELIVERED_BUT_DAMAGED".equalsIgnoreCase(caseType)) {
            return "REQUIRES_DAMAGE_REVIEW";
        }
        if ("HIGH_RISK_SETTLEMENT_HOLD".equalsIgnoreCase(caseType)) {
            return "RESTRICTED_PENDING_RISK_CLEARANCE";
        }
        if ("SELLER_CANCELLATION_AFTER_AUTHORIZATION".equalsIgnoreCase(caseType)) {
            return "ELIGIBLE_FOR_REFUND_REVIEW";
        }
        return "HELD".equalsIgnoreCase(holdState)
                ? "ELIGIBLE_FOR_CONTINUED_HOLD"
                : "ELIGIBLE_FOR_REFUND_REVIEW";
    }

    String summary(EscrowRecord record, String caseType) {
        if ("REFUNDED".equalsIgnoreCase(record.holdState())) {
            return "Escrow recorded a completed refund for the case and retains the settlement audit trail.";
        }
        if ("CONTINUED_HOLD_RECORDED".equalsIgnoreCase(record.priorSettlementStatus())) {
            return "Escrow still holds the funds and the latest settlement decision recorded a continued hold.";
        }
        if ("DELIVERED_BUT_DAMAGED".equalsIgnoreCase(caseType)) {
            return "Escrow still holds the funds while damage evidence and seller response are collected.";
        }
        if ("HIGH_RISK_SETTLEMENT_HOLD".equalsIgnoreCase(caseType)) {
            return "Escrow still holds the funds because the case is under a high-risk settlement hold.";
        }
        if ("SELLER_CANCELLATION_AFTER_AUTHORIZATION".equalsIgnoreCase(caseType)) {
            return "Escrow still holds the authorized funds because the seller cancelled before fulfillment completed.";
        }
        return "Escrow still holds the authorized funds and no prior refund has been executed.";
    }

    private String evidencePrompt(EscrowContracts.EscrowEvidenceRequest request) {
        return String.join("\n",
                "mode=evidence-summary",
                "caseId=" + blankSafe(request.caseId()),
                "caseType=" + blankSafe(request.caseType()),
                "orderId=" + blankSafe(request.orderId()),
                "disputeSummary=" + blankSafe(request.disputeSummary()),
                "amount=" + request.amount(),
                "currency=" + blankSafe(request.currency()),
                "operatorId=" + blankSafe(request.operatorId()),
                "operatorRole=" + blankSafe(request.operatorRole()),
                "instructions=Call escrow_case_lookup exactly once, then respond through structured_output with holdState, settlementEligibility, amount, currency, priorSettlementStatus, and summary.");
    }

    private String reviewPrompt(EscrowContracts.EscrowSpecialistReviewRequest request) {
        return String.join("\n",
                "mode=specialist-review",
                "caseId=" + blankSafe(request.caseId()),
                "caseType=" + blankSafe(request.caseType()),
                "orderId=" + blankSafe(request.orderId()),
                "disputeSummary=" + blankSafe(request.disputeSummary()),
                "amount=" + request.amount(),
                "currency=" + blankSafe(request.currency()),
                "operatorId=" + blankSafe(request.operatorId()),
                "operatorRole=" + blankSafe(request.operatorRole()),
                "instruction=" + blankSafe(request.instruction()),
                "instructions=Call escrow_case_lookup exactly once, then respond through structured_output with a concise escrow specialist summary in the summary field.");
    }

    private String blankSafe(String value) {
        return value == null ? "" : value;
    }
}