package com.mahitotsu.arachne.samples.delivery.paymentservice.api;

import static com.mahitotsu.arachne.samples.delivery.paymentservice.domain.PaymentTypes.*;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mahitotsu.arachne.samples.delivery.paymentservice.application.PaymentApplicationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(path = "/internal/payment", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Payment Service", description = "Payment-service endpoint for deterministic payment preparation and optional charge execution.")
public class PaymentController {

    private final PaymentApplicationService applicationService;

    PaymentController(PaymentApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping("/prepare")
    @Operation(
            summary = "Prepare payment",
            description = "Accepts the checkout intent and total, then returns the prepared payment state and optional charge result.",
            extensions = @Extension(name = "x-ai-prompt-contract", properties = {
                    @ExtensionProperty(name = "agent", value = "payment-service"),
                    @ExtensionProperty(name = "contract", value = "{\"requiredInputs\":[{\"field\":\"message\",\"meaning\":\"Customer payment intent or checkout instruction.\"},{\"field\":\"total\",\"meaning\":\"Deterministic total that payment-service must prepare or charge.\"}],\"optionalInputs\":[{\"field\":\"confirmRequested\",\"meaning\":\"Whether the request should only prepare payment or actually execute the charge.\"},{\"field\":\"sessionId\",\"meaning\":\"Correlation id for the parent workflow.\"}]}", parseValue = true)
            }))
    PaymentPrepareResponse prepare(@RequestBody PaymentPrepareRequest request) {
        return applicationService.prepare(request);
    }
}