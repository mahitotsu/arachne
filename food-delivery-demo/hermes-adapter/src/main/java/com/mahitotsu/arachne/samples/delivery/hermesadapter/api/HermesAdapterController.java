package com.mahitotsu.arachne.samples.delivery.hermesadapter.api;

import static com.mahitotsu.arachne.samples.delivery.hermesadapter.domain.HermesAdapterTypes.*;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mahitotsu.arachne.samples.delivery.hermesadapter.application.HermesAdapterService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(path = "/adapter", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Hermes Adapter", description = "External high-speed partner adapter endpoints.")
public class HermesAdapterController {

    private final HermesAdapterService adapterService;

    HermesAdapterController(HermesAdapterService adapterService) {
        this.adapterService = adapterService;
    }

    @PostMapping("/eta")
    @Operation(summary = "Quote Hermes ETA", description = "Returns Hermes partner ETA, congestion, fee, and availability for the supplied order context.")
    ResponseEntity<AdapterEtaResponse> eta(@RequestBody AdapterEtaRequest request) {
        AdapterEtaResponse response = adapterService.quote(request);
        return response.status().equals("AVAILABLE")
                ? ResponseEntity.ok(response)
                : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    @GetMapping("/health")
    @Operation(summary = "Read Hermes adapter health", description = "Returns the current Hermes availability state exposed to delivery-service and registry-service.")
    ResponseEntity<AdapterHealthResponse> health() {
        boolean available = adapterService.available();
        AdapterHealthResponse response = new AdapterHealthResponse(available ? "AVAILABLE" : "NOT_AVAILABLE", "hermes-adapter");
        return available ? ResponseEntity.ok(response) : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }
}