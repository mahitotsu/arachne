package com.mahitotsu.arachne.samples.delivery.orderservice;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/orders", produces = MediaType.APPLICATION_JSON_VALUE)
class OrderHistoryController {

    private final OrderApplicationService applicationService;

    OrderHistoryController(OrderApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping("/history")
    List<StoredOrderSummary> orderHistory() {
        return applicationService.recentOrderHistory();
    }
}