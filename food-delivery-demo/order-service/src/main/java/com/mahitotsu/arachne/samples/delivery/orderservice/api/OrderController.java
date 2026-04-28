package com.mahitotsu.arachne.samples.delivery.orderservice.api;

import static com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.*;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mahitotsu.arachne.samples.delivery.orderservice.application.OrderApplicationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(path = "/api/order", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Order Workflow", description = "Public order workflow endpoints exposed by order-service.")
public class OrderController {

    private final OrderApplicationService applicationService;

    OrderController(OrderApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @Operation(
            summary = "Suggest order items",
            description = "Accepts the current order intent or a refinement and returns proposal items, ETA, workflow state, and service traces.",
            extensions = @Extension(name = "x-ai-prompt-contract", properties = {
                    @ExtensionProperty(name = "agent", value = "menu-agent"),
                    @ExtensionProperty(name = "contract", value = "{\"requiredInputs\":[{\"field\":\"message\",\"meaning\":\"Natural-language order intent for this turn.\"}],\"optionalInputs\":[{\"field\":\"refinement\",\"meaning\":\"Additional constraints for the previous suggestion.\"},{\"field\":\"locale\",\"meaning\":\"Response language hint.\"}],\"serviceBehavior\":\"If the message implies repeat order intent, order-service may enrich the downstream menu prompt with recent-order context.\"}", parseValue = true)
            }))
    @PostMapping(path = "/suggest", consumes = MediaType.APPLICATION_JSON_VALUE)
    SuggestOrderResponse suggest(@RequestBody SuggestOrderRequest request) {
        return applicationService.suggest(request);
    }

    @Operation(
            summary = "Confirm selected items",
            description = "Accepts the selected proposal items and returns delivery candidates together with the updated order draft.")
    @PostMapping(path = "/confirm-items", consumes = MediaType.APPLICATION_JSON_VALUE)
    ConfirmItemsResponse confirmItems(@RequestBody ConfirmItemsRequest request) {
        return applicationService.confirmItems(request);
    }

    @Operation(
            summary = "Confirm delivery lane",
            description = "Accepts the selected delivery code and returns the payment summary and updated order draft.")
    @PostMapping(path = "/confirm-delivery", consumes = MediaType.APPLICATION_JSON_VALUE)
    ConfirmDeliveryResponse confirmDelivery(@RequestBody ConfirmDeliveryRequest request) {
        return applicationService.confirmDelivery(request);
    }

    @Operation(
            summary = "Confirm payment",
            description = "Places the order using the prepared payment and returns the confirmed draft and service trace.")
    @PostMapping(path = "/confirm-payment", consumes = MediaType.APPLICATION_JSON_VALUE)
    ConfirmPaymentResponse confirmPayment(@RequestBody ConfirmPaymentRequest request) {
        return applicationService.confirmPayment(request);
    }

    @Operation(
            summary = "Restore order session",
            description = "Restores the current workflow stage, pending proposal, delivery options, and draft for the session.")
    @GetMapping("/session/{sessionId}")
    OrderSessionView session(@PathVariable String sessionId) {
        return applicationService.session(sessionId);
    }
}