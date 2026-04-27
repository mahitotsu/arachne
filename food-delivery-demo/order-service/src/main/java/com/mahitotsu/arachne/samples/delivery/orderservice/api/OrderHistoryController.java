package com.mahitotsu.arachne.samples.delivery.orderservice.api;

import static com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.*;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mahitotsu.arachne.samples.delivery.orderservice.application.OrderApplicationService;

@RestController
@RequestMapping(path = "/api/orders", produces = MediaType.APPLICATION_JSON_VALUE)
public class OrderHistoryController {

    private final OrderApplicationService applicationService;

    OrderHistoryController(OrderApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping("/history")
    List<StoredOrderSummary> orderHistory() {
        return applicationService.recentOrderHistory();
    }
}