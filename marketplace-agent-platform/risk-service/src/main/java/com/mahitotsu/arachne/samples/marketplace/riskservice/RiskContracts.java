package com.mahitotsu.arachne.samples.marketplace.riskservice;

import java.util.List;

final class RiskContracts {

    private RiskContracts() {
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

    record RiskSpecialistReviewRequest(
            String caseId,
            String caseType,
            String orderId,
            String disputeSummary,
            String operatorRole,
            String instruction) {
    }

    record RiskSpecialistReview(
            String summary) {
    }
}