package com.mahitotsu.arachne.samples.marketplace.riskservice;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/risk")
class RiskController {

    private final RiskApplicationService applicationService;

    RiskController(RiskApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping("/case-review")
    RiskContracts.RiskReviewSummary caseReview(@RequestBody RiskContracts.RiskCaseReviewRequest request) {
        return applicationService.caseReview(request);
    }
}