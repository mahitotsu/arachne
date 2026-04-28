package com.mahitotsu.arachne.samples.delivery.deliveryservice.api;

import static com.mahitotsu.arachne.samples.delivery.deliveryservice.domain.DeliveryTypes.*;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mahitotsu.arachne.samples.delivery.deliveryservice.application.DeliveryApplicationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(path = "/internal/delivery", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Delivery Service", description = "Delivery-service endpoint for quote generation and lane ranking.")
public class DeliveryController {

    private final DeliveryApplicationService applicationService;

    DeliveryController(DeliveryApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping("/quote")
    @Operation(
            summary = "Quote delivery options",
            description = "Accepts customer delivery intent plus item names and returns ranked in-house and external delivery options.",
            extensions = @Extension(name = "x-ai-prompt-contract", properties = {
                    @ExtensionProperty(name = "agent", value = "delivery-agent"),
                    @ExtensionProperty(name = "contract", value = "{\"requiredInputs\":[{\"field\":\"message\",\"meaning\":\"Natural-language delivery preference such as speed or price.\"},{\"field\":\"itemNames\",\"meaning\":\"Names of the items included in the delivery quote.\"}],\"optionalInputs\":[{\"field\":\"sessionId\",\"meaning\":\"Correlation id for the parent workflow.\"}]}", parseValue = true)
            }))
    DeliveryQuoteResponse quote(@RequestBody DeliveryQuoteRequest request) {
        return applicationService.quote(request);
    }
}