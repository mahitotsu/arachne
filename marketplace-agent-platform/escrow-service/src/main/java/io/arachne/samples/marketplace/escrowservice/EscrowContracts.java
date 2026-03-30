package io.arachne.samples.marketplace.escrowservice;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

final class EscrowContracts {

    private EscrowContracts() {
    }

    record EscrowEvidenceRequest(
            String caseId,
            String caseType,
            String orderId,
            BigDecimal amount,
            String currency,
            String operatorId,
            String operatorRole) {
    }

    record EscrowEvidenceSummary(
            String holdState,
            String settlementEligibility,
            BigDecimal amount,
            String currency,
            String priorSettlementStatus,
            String summary) {
    }

    record ExecuteSettlementCommand(
            String caseId,
            String action,
            String actorId,
            String actorRole,
            BigDecimal amount,
            String currency) {
    }

    record SettlementOutcome(
            String outcomeType,
            String outcomeStatus,
            OffsetDateTime settledAt,
            String settlementReference,
            String summary) {
    }
}