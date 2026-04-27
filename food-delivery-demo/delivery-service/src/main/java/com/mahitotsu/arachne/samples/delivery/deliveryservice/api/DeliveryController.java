package com.mahitotsu.arachne.samples.delivery.deliveryservice.api;

import static com.mahitotsu.arachne.samples.delivery.deliveryservice.domain.DeliveryTypes.*;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mahitotsu.arachne.samples.delivery.deliveryservice.application.DeliveryApplicationService;

@RestController
@RequestMapping(path = "/internal/delivery", produces = MediaType.APPLICATION_JSON_VALUE)
public class DeliveryController {

    private final DeliveryApplicationService applicationService;

    DeliveryController(DeliveryApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping("/quote")
    DeliveryQuoteResponse quote(@RequestBody DeliveryQuoteRequest request) {
        return applicationService.quote(request);
    }
}