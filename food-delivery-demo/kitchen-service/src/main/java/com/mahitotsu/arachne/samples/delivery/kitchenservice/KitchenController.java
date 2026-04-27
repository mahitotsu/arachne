package com.mahitotsu.arachne.samples.delivery.kitchenservice;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/internal/kitchen", produces = MediaType.APPLICATION_JSON_VALUE)
class KitchenController {

    private final KitchenApplicationService applicationService;

    KitchenController(KitchenApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping("/check")
    KitchenCheckResponse check(@RequestBody KitchenCheckRequest request) {
        return applicationService.check(request);
    }
}