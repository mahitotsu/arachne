package com.mahitotsu.arachne.samples.delivery.supportservice.api;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mahitotsu.arachne.samples.delivery.supportservice.application.SupportApplicationService;
import com.mahitotsu.arachne.samples.delivery.supportservice.domain.CampaignSummary;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(path = "/api/support", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Support Service", description = "Support-service endpoints for chat, feedback intake, campaigns, and service status.")
public class SupportController {

    private final SupportApplicationService applicationService;

    SupportController(SupportApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping(path = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Start a support chat turn",
            description = "Accepts an authenticated support inquiry and returns FAQ, campaign, status, and recent-order context.",
            extensions = @Extension(name = "x-ai-prompt-contract", properties = {
                    @ExtensionProperty(name = "agent", value = "support-agent"),
                    @ExtensionProperty(name = "contract", value = "{\"requiredInputs\":[{\"field\":\"message\",\"meaning\":\"Natural-language support inquiry for this turn.\"}],\"optionalInputs\":[{\"field\":\"sessionId\",\"meaning\":\"Conversation session identifier used to continue a support chat.\"}],\"implicitContext\":[{\"field\":\"customerId\",\"source\":\"JWT authentication\",\"meaning\":\"Authenticated customer context injected into the agent prompt.\"}]}", parseValue = true)
            }))
    public SupportChatResponse chat(@RequestBody SupportChatRequest request) {
        return applicationService.chat(request);
    }

    @PostMapping(path = "/feedback", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Record support feedback",
            description = "Accepts a post-order support message and returns classification plus escalation guidance.",
            extensions = @Extension(name = "x-ai-prompt-contract", properties = {
                    @ExtensionProperty(name = "agent", value = "support-agent"),
                    @ExtensionProperty(name = "contract", value = "{\"requiredInputs\":[{\"field\":\"message\",\"meaning\":\"Free-text feedback or support message to classify.\"}],\"optionalInputs\":[{\"field\":\"orderId\",\"meaning\":\"Related order identifier when the feedback concerns a specific order.\"},{\"field\":\"rating\",\"meaning\":\"Optional numeric rating supplied by the customer.\"}]}", parseValue = true)
            }))
    public SupportFeedbackResponse feedback(@RequestBody SupportFeedbackRequest request) {
        return applicationService.feedback(request);
    }

    @GetMapping("/campaigns")
    @Operation(summary = "List active campaigns", description = "Returns the active campaigns that can be surfaced by support-service.")
    public List<CampaignSummary> campaigns() {
        return applicationService.campaigns();
    }

    @GetMapping("/status")
    @Operation(summary = "Read current service status", description = "Returns the current aggregated service-status view retrieved through registry-service.")
    public SupportStatusResponse status() {
        return applicationService.status();
    }
}