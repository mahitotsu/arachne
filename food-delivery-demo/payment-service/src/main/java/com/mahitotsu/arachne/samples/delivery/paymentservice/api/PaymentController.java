package com.mahitotsu.arachne.samples.delivery.paymentservice.api;

import static com.mahitotsu.arachne.samples.delivery.paymentservice.domain.PaymentTypes.*;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mahitotsu.arachne.samples.delivery.paymentservice.application.PaymentApplicationService;

@RestController
@RequestMapping(path = "/internal/payment", produces = MediaType.APPLICATION_JSON_VALUE)
public class PaymentController {

    private final PaymentApplicationService applicationService;

    PaymentController(PaymentApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping("/prepare")
    PaymentPrepareResponse prepare(@RequestBody PaymentPrepareRequest request) {
        return applicationService.prepare(request);
    }
}