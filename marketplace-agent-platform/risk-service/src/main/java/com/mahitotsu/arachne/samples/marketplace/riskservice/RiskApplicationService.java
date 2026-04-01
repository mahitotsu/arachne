package com.mahitotsu.arachne.samples.marketplace.riskservice;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
class RiskApplicationService {

    private final RiskRepository repository;

    RiskApplicationService(RiskRepository repository) {
        this.repository = repository;
    }

    RiskContracts.RiskReviewSummary caseReview(RiskContracts.RiskCaseReviewRequest request) {
        repository.ensureRiskReview(request.caseId(), request.orderId(), request.operatorRole());
        var review = repository.findRiskReview(request.caseId())
                .orElseThrow(() -> new IllegalStateException("Risk review missing after initialization for case " + request.caseId()));
        return new RiskContracts.RiskReviewSummary(
                review.indicatorSummary(),
                review.manualReviewRequired(),
                review.policyFlags(),
                review.summary());
    }
}