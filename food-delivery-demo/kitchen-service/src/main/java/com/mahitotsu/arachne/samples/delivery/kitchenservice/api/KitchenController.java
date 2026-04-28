package com.mahitotsu.arachne.samples.delivery.kitchenservice.api;

import static com.mahitotsu.arachne.samples.delivery.kitchenservice.domain.KitchenTypes.*;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mahitotsu.arachne.samples.delivery.kitchenservice.application.KitchenApplicationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(path = "/internal/kitchen", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Kitchen Service", description = "Kitchen-service endpoint for availability checks and substitute validation.")
public class KitchenController {

    private final KitchenApplicationService applicationService;

    KitchenController(KitchenApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping("/check")
    @Operation(
            summary = "Check kitchen availability",
            description = "Accepts selected item ids plus the original customer intent and returns availability, prep timing, and substitute collaboration traces.",
            extensions = @Extension(name = "x-ai-prompt-contract", properties = {
                    @ExtensionProperty(name = "agent", value = "kitchen-agent"),
                    @ExtensionProperty(name = "contract", value = "{\"requiredInputs\":[{\"field\":\"itemIds\",\"meaning\":\"Selected menu item ids to validate for availability and prep timing.\"},{\"field\":\"message\",\"meaning\":\"Original customer intent used to evaluate substitutes and priorities.\"}],\"optionalInputs\":[{\"field\":\"sessionId\",\"meaning\":\"Correlation id for the parent workflow.\"}]}", parseValue = true)
            }))
    KitchenCheckResponse check(@RequestBody KitchenCheckRequest request) {
        return applicationService.check(request);
    }
}