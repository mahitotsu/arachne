package com.mahitotsu.arachne.samples.delivery.orderservice.api;

import static com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.*;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mahitotsu.arachne.samples.delivery.orderservice.application.OrderApplicationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(path = "/api/orders", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Order History", description = "Read-only order history endpoints exposed by order-service.")
public class OrderHistoryController {

    private final OrderApplicationService applicationService;

    OrderHistoryController(OrderApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @Operation(
            summary = "List recent order history",
            description = "Returns the most recent confirmed orders for the authenticated customer.")
    @GetMapping("/history")
    List<StoredOrderSummary> orderHistory() {
        return applicationService.recentOrderHistory();
    }
}