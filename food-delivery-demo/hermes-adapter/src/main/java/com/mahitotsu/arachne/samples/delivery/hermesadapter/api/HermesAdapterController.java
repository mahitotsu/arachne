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
@Tag(name = "Hermes Adapter", description = "外部高速配送パートナー Hermes のアダプターエンドポイントです。")
public class HermesAdapterController {

    private final HermesAdapterService adapterService;

    HermesAdapterController(HermesAdapterService adapterService) {
        this.adapterService = adapterService;
    }

    @PostMapping("/eta")
    @Operation(summary = "Quote Hermes ETA", description = "指定した注文コンテキストに対する Hermes の ETA、混雑、料金、可用性を返します。")
    ResponseEntity<AdapterEtaResponse> eta(@RequestBody AdapterEtaRequest request) {
        AdapterEtaResponse response = adapterService.quote(request);
        return response.status().equals("AVAILABLE")
                ? ResponseEntity.ok(response)
                : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    @GetMapping("/health")
    @Operation(summary = "Read Hermes adapter health", description = "delivery-service と registry-service に公開する現在の Hermes 可用状態を返します。")
    ResponseEntity<AdapterHealthResponse> health() {
        boolean available = adapterService.available();
        AdapterHealthResponse response = new AdapterHealthResponse(available ? "AVAILABLE" : "NOT_AVAILABLE", "hermes-adapter");
        return available ? ResponseEntity.ok(response) : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }
}