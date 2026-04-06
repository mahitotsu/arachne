package com.mahitotsu.arachne.samples.marketplace.riskservice;

import java.util.List;

import com.mahitotsu.arachne.strands.spring.AgentFactory;
import com.mahitotsu.arachne.strands.tool.Tool;
import org.springframework.stereotype.Service;

@Service
class RiskApplicationService {

    private final RiskRepository repository;
    private final AgentFactory agentFactory;
    private final Tool riskReviewLookupTool;

    RiskApplicationService(RiskRepository repository, AgentFactory agentFactory, Tool riskReviewLookupTool) {
        this.repository = repository;
        this.agentFactory = agentFactory;
        this.riskReviewLookupTool = riskReviewLookupTool;
    }

    RiskContracts.RiskReviewSummary caseReview(RiskContracts.RiskCaseReviewRequest request) {
        repository.ensureRiskReview(request.caseId(), request.orderId(), request.caseType(), request.disputeSummary(), request.operatorRole());
        return agentFactory.builder("risk-agent")
                .tools(riskReviewLookupTool)
                .build()
                .run(reviewPrompt(request), RiskContracts.RiskReviewSummary.class);
    }

    RiskContracts.RiskSpecialistReview specialistReview(RiskContracts.RiskSpecialistReviewRequest request) {
        repository.ensureRiskReview(request.caseId(), request.orderId(), request.caseType(), request.disputeSummary(), request.operatorRole());
        return agentFactory.builder("risk-agent")
                .tools(riskReviewLookupTool)
                .build()
                .run(followUpPrompt(request), RiskContracts.RiskSpecialistReview.class);
    }

    private String reviewPrompt(RiskContracts.RiskCaseReviewRequest request) {
        return String.join("\n",
                "mode=evidence-summary",
                "caseId=" + blankSafe(request.caseId()),
                "caseType=" + blankSafe(request.caseType()),
                "orderId=" + blankSafe(request.orderId()),
                "disputeSummary=" + blankSafe(request.disputeSummary()),
                "operatorRole=" + blankSafe(request.operatorRole()),
                "instructions=Call risk_review_lookup exactly once, then respond through structured_output with indicatorSummary, manualReviewRequired, policyFlags, and summary.");
    }

    private String followUpPrompt(RiskContracts.RiskSpecialistReviewRequest request) {
        return String.join("\n",
                "mode=specialist-review",
                "caseId=" + blankSafe(request.caseId()),
                "caseType=" + blankSafe(request.caseType()),
                "orderId=" + blankSafe(request.orderId()),
                "disputeSummary=" + blankSafe(request.disputeSummary()),
                "operatorRole=" + blankSafe(request.operatorRole()),
                "instruction=" + blankSafe(request.instruction()),
                "instructions=Call risk_review_lookup exactly once, then respond through structured_output with a concise risk specialist summary in the summary field.");
    }

    private String blankSafe(String value) {
        return value == null ? "" : value;
    }
}