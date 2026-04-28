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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(path = "/registry", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Registry Service", description = "Registry-service endpoints for capability registration, discovery, and health aggregation.")
public class RegistryController {

    private final RegistryApplicationService applicationService;

    RegistryController(RegistryApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a service descriptor", description = "Registers a service capability, endpoint, agent, and health metadata in registry-service.")
    RegistryServiceDescriptor register(@RequestBody RegistryRegistration request) {
        return applicationService.register(request);
    }

    @PostMapping("/discover")
    @Operation(
            summary = "Discover registered services",
            description = "Accepts a natural-language capability query and returns matching service descriptors plus the registry agent summary.",
            extensions = @Extension(name = "x-ai-prompt-contract", properties = {
                    @ExtensionProperty(name = "agent", value = "capability-registry-agent"),
                    @ExtensionProperty(name = "contract", value = "{\"requiredInputs\":[{\"field\":\"query\",\"meaning\":\"Natural-language discovery request for capabilities or service types.\"}],\"optionalInputs\":[{\"field\":\"availableOnly\",\"meaning\":\"Whether the result set should be filtered to currently available services.\"}]}", parseValue = true)
            }))
    RegistryDiscoverResponse discover(@RequestBody RegistryDiscoverRequest request) {
        return applicationService.discover(request);
    }

    @GetMapping("/services")
    @Operation(summary = "List registered services", description = "Returns the current registry of service descriptors, including inactive adapters retained for discovery visibility.")
    List<RegistryServiceDescriptor> services() {
        return applicationService.services();
    }

    @GetMapping("/health")
    @Operation(summary = "Read aggregated service health", description = "Returns the current health view across all registered services.")
    RegistryHealthResponse health() {
        return applicationService.health();
    }
}