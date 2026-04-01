package com.mahitotsu.arachne.samples.marketplace.escrowservice;

import java.time.Clock;
import java.time.OffsetDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
class EscrowApplicationService {

    private final Clock clock;
    private final EscrowRepository repository;

    EscrowApplicationService(Clock clock, EscrowRepository repository) {
        this.clock = clock;
        this.repository = repository;
    }

    EscrowContracts.EscrowEvidenceSummary evidenceSummary(EscrowContracts.EscrowEvidenceRequest request) {
        repository.ensureCaseRecord(request.caseId(), request.amount(), request.currency());
        var record = repository.findCaseRecord(request.caseId())
                .orElseThrow(() -> new IllegalStateException("Escrow record missing after initialization for case " + request.caseId()));
        return new EscrowContracts.EscrowEvidenceSummary(
                record.holdState(),
                settlementEligibility(record.holdState()),
                record.amount(),
                record.currency(),
                record.priorSettlementStatus(),
                summary(record));
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

    private String settlementEligibility(String holdState) {
        return "HELD".equalsIgnoreCase(holdState)
                ? "ELIGIBLE_FOR_CONTINUED_HOLD"
                : "ELIGIBLE_FOR_REFUND_REVIEW";
    }

    private String summary(EscrowRecord record) {
        if ("REFUNDED".equalsIgnoreCase(record.holdState())) {
            return "Escrow recorded a completed refund for the case and retains the settlement audit trail.";
        }
        if ("CONTINUED_HOLD_RECORDED".equalsIgnoreCase(record.priorSettlementStatus())) {
            return "Escrow still holds the funds and the latest settlement decision recorded a continued hold.";
        }
        return "Escrow still holds the authorized funds and no prior refund has been executed.";
    }
}