package com.mahitotsu.arachne.samples.marketplace.workflowservice;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

final class DownstreamContracts {

    private DownstreamContracts() {
    }

    record EscrowEvidenceRequest(
            String caseId,
            String caseType,
            String orderId,
            String disputeSummary,
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

    record ShipmentEvidenceRequest(
            String caseId,
            String caseType,
            String disputeSummary,
            String orderId) {
    }

    record ShipmentEvidenceSummary(
            String trackingNumber,
            String milestoneSummary,
            String deliveryConfidence,
            String shippingExceptionSummary) {
    }

    record RiskCaseReviewRequest(
            String caseId,
            String caseType,
            String orderId,
            String disputeSummary,
            String operatorRole) {
    }

    record RiskReviewSummary(
            String indicatorSummary,
            boolean manualReviewRequired,
            List<String> policyFlags,
            String summary) {
    }

    record NotificationDispatchCommand(
            String caseId,
            String outcomeType,
            String outcomeStatus,
            String settlementReference) {
    }

    record NotificationDispatchResult(
            String dispatchStatus,
            String deliveryStatus,
            String summary) {
    }
}