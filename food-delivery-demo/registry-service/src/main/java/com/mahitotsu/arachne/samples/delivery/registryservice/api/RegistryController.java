package com.mahitotsu.arachne.samples.delivery.registryservice.api;

import static com.mahitotsu.arachne.samples.delivery.registryservice.domain.RegistryTypes.*;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mahitotsu.arachne.samples.delivery.registryservice.application.RegistryApplicationService;

@RestController
@RequestMapping(path = "/registry", produces = MediaType.APPLICATION_JSON_VALUE)
public class RegistryController {

    private final RegistryApplicationService applicationService;

    RegistryController(RegistryApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping("/register")
    RegistryServiceDescriptor register(@RequestBody RegistryRegistration request) {
        return applicationService.register(request);
    }

    @PostMapping("/discover")
    RegistryDiscoverResponse discover(@RequestBody RegistryDiscoverRequest request) {
        return applicationService.discover(request);
    }

    @GetMapping("/services")
    List<RegistryServiceDescriptor> services() {
        return applicationService.services();
    }

    @GetMapping("/health")
    RegistryHealthResponse health() {
        return applicationService.health();
    }
}