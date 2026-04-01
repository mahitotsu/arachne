package com.mahitotsu.arachne.samples.marketplace.riskservice;

import java.util.List;

final class RiskContracts {

    private RiskContracts() {
    }

    record RiskCaseReviewRequest(
            String caseId,
            String caseType,
            String orderId,
            String operatorRole) {
    }

    record RiskReviewSummary(
            String indicatorSummary,
            boolean manualReviewRequired,
            List<String> policyFlags,
            String summary) {
    }
}