package com.mahitotsu.arachne.samples.delivery.orderservice;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/order", produces = MediaType.APPLICATION_JSON_VALUE)
class OrderController {

    private final OrderApplicationService applicationService;

    OrderController(OrderApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping(path = "/suggest", consumes = MediaType.APPLICATION_JSON_VALUE)
    SuggestOrderResponse suggest(@RequestBody SuggestOrderRequest request) {
        return applicationService.suggest(request);
    }

    @PostMapping(path = "/confirm-items", consumes = MediaType.APPLICATION_JSON_VALUE)
    ConfirmItemsResponse confirmItems(@RequestBody ConfirmItemsRequest request) {
        return applicationService.confirmItems(request);
    }

    @PostMapping(path = "/confirm-delivery", consumes = MediaType.APPLICATION_JSON_VALUE)
    ConfirmDeliveryResponse confirmDelivery(@RequestBody ConfirmDeliveryRequest request) {
        return applicationService.confirmDelivery(request);
    }

    @PostMapping(path = "/confirm-payment", consumes = MediaType.APPLICATION_JSON_VALUE)
    ConfirmPaymentResponse confirmPayment(@RequestBody ConfirmPaymentRequest request) {
        return applicationService.confirmPayment(request);
    }

    @GetMapping("/session/{sessionId}")
    OrderSessionView session(@PathVariable String sessionId) {
        return applicationService.session(sessionId);
    }
}