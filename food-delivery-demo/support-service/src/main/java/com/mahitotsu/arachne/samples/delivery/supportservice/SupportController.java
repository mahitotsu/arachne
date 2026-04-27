package com.mahitotsu.arachne.samples.delivery.supportservice;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/support", produces = MediaType.APPLICATION_JSON_VALUE)
class SupportController {

    private final SupportApplicationService applicationService;

    SupportController(SupportApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping(path = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE)
    SupportChatResponse chat(@RequestBody SupportChatRequest request) {
        return applicationService.chat(request);
    }

    @PostMapping(path = "/feedback", consumes = MediaType.APPLICATION_JSON_VALUE)
    SupportFeedbackResponse feedback(@RequestBody SupportFeedbackRequest request) {
        return applicationService.feedback(request);
    }

    @GetMapping("/campaigns")
    List<CampaignSummary> campaigns() {
        return applicationService.campaigns();
    }

    @GetMapping("/status")
    SupportStatusResponse status() {
        return applicationService.status();
    }
}