package com.mahitotsu.arachne.samples.marketplace.workflowservice;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import com.mahitotsu.arachne.samples.marketplace.workflowservice.WorkflowContracts.Recommendation;

final class ScenarioDrivenRecommendation {

    private ScenarioDrivenRecommendation() {
    }

    static Assessment assess(String caseType, BigDecimal amount, WorkflowRuntimeAdapter.RawEvidence rawEvidence) {
        return assess(
                caseType,
                amount,
                rawEvidence.shipment().deliveryConfidence(),
                rawEvidence.shipment().milestoneSummary(),
                rawEvidence.shipment().shippingExceptionSummary(),
                rawEvidence.escrow().holdState(),
                rawEvidence.escrow().priorSettlementStatus(),
                rawEvidence.risk().manualReviewRequired(),
                rawEvidence.risk().policyFlags(),
                rawEvidence.risk().indicatorSummary(),
                rawEvidence.risk().summary());
    }

    static Assessment assess(
            String caseType,
            BigDecimal amount,
            String deliveryConfidence,
            String milestoneSummary,
            String shippingExceptionSummary,
            String holdState,
            String priorSettlementStatus,
            boolean manualReviewRequired,
            List<String> policyFlags,
            String indicatorSummary,
            String riskSummary) {
        String normalizedCaseType = normalize(caseType);
        String shipmentText = normalize(milestoneSummary) + " " + normalize(shippingExceptionSummary);
        String riskText = normalize(indicatorSummary) + " " + normalize(riskSummary);
        List<String> normalizedFlags = policyFlags == null
                ? List.of()
                : policyFlags.stream().map(ScenarioDrivenRecommendation::normalize).toList();
        boolean explicitNoElevatedFraud = riskText.contains("no elevated fraud");
        boolean highRisk = normalizedCaseType.contains("high_risk")
                || (riskText.contains("elevated fraud") && !explicitNoElevatedFraud)
                || riskText.contains("account takeover")
                || riskText.contains("high-risk")
                || normalizedFlags.stream().anyMatch(flag -> flag.contains("high_risk") || flag.contains("account_takeover"));
        boolean fundsHeld = "held".equals(normalize(holdState));
        boolean noPriorRefund = normalize(priorSettlementStatus).isBlank()
                || normalize(priorSettlementStatus).contains("no_prior_refund");
        boolean delivered = "high".equals(normalize(deliveryConfidence))
                || shipmentText.contains("delivered")
                || shipmentText.contains("proof of delivery");
        boolean damaged = normalizedCaseType.contains("damaged")
                || shipmentText.contains("damaged")
                || shipmentText.contains("crushed")
                || shipmentText.contains("wet package")
                || shipmentText.contains("impact");
        boolean sellerCancelled = normalizedCaseType.contains("seller_cancellation")
                || shipmentText.contains("voided before carrier handoff")
                || shipmentText.contains("seller cancelled")
                || shipmentText.contains("label voided");
        boolean notDelivered = shipmentText.contains("not-delivered")
                || shipmentText.contains("not delivered")
                || shipmentText.contains("no final delivery scan")
                || shipmentText.contains("stuck in transit")
                || shipmentText.contains("no carrier delivery confirmation");

        if (highRisk) {
            return new Assessment(
                    Recommendation.CONTINUED_HOLD,
                    "Workflow recommends a continued hold because the case carries elevated risk indicators and settlement should stay frozen until the risk posture is cleared.",
                    true);
        }

        if (normalizedCaseType.contains("delivered_but_damaged") || damaged) {
            return new Assessment(
                    Recommendation.PENDING_MORE_EVIDENCE,
                    "Workflow recommends gathering more evidence because the shipment appears delivered but the damage claim still needs inspection proof and seller-side review.",
                    false);
        }

        if (normalizedCaseType.contains("seller_cancellation_after_authorization") || sellerCancelled) {
            if (fundsHeld && noPriorRefund) {
                return new Assessment(
                        Recommendation.REFUND,
                        "Workflow recommends a refund because the seller cancelled after authorization and the held funds can be returned to the buyer.",
                        true);
            }
            return new Assessment(
                    Recommendation.CONTINUED_HOLD,
                    "Workflow recommends a continued hold because the seller cancellation path still needs settlement verification before funds move.",
                    true);
        }

        if (normalizedCaseType.contains("item_not_received")) {
            if (delivered && !notDelivered) {
                return new Assessment(
                        Recommendation.PENDING_MORE_EVIDENCE,
                        "Workflow recommends gathering more evidence because delivery signals conflict with the item-not-received claim.",
                        false);
            }
            if (fundsHeld && noPriorRefund && notDelivered
                    && amount != null
                    && amount.compareTo(WorkflowRuntimeAdapter.AUTOMATED_REFUND_THRESHOLD) <= 0
                    && !highRisk) {
                return new Assessment(
                        Recommendation.REFUND,
                        "Workflow recommends a refund after confirming non-delivery on a low-value dispute while funds remain held.",
                        true);
            }
            if (manualReviewRequired && amount != null
                    && amount.compareTo(WorkflowRuntimeAdapter.AUTOMATED_REFUND_THRESHOLD) > 0) {
                return new Assessment(
                        Recommendation.CONTINUED_HOLD,
                        "Workflow recommends keeping the hold because the non-delivery claim is higher value and still requires finance-controlled settlement review.",
                        true);
            }
            if (notDelivered) {
                return new Assessment(
                        Recommendation.CONTINUED_HOLD,
                        "Workflow recommends keeping the hold until finance control confirms the next step on the unresolved non-delivery dispute.",
                        true);
            }
        }

        return new Assessment(
                Recommendation.PENDING_MORE_EVIDENCE,
                "Workflow recommends gathering more evidence before it can choose a settlement path for the current dispute.",
                false);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    record Assessment(Recommendation recommendation, String message, boolean approvalRequired) {
    }
}