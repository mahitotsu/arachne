package com.mahitotsu.arachne.samples.delivery.idatenadapter.api;

import static com.mahitotsu.arachne.samples.delivery.idatenadapter.domain.IdatenAdapterTypes.*;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mahitotsu.arachne.samples.delivery.idatenadapter.application.IdatenAdapterService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(path = "/adapter", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Idaten Adapter", description = "外部低コスト配送パートナー Idaten のアダプターエンドポイントです。")
public class IdatenAdapterController {

    private final IdatenAdapterService adapterService;

    IdatenAdapterController(IdatenAdapterService adapterService) {
        this.adapterService = adapterService;
    }

    @PostMapping("/eta")
    @Operation(summary = "Quote Idaten ETA", description = "指定した注文コンテキストに対する Idaten の ETA、混雑、料金、可用性を返します。")
    AdapterEtaResponse eta(@RequestBody AdapterEtaRequest request) {
        return adapterService.quote(request);
    }

    @GetMapping("/health")
    @Operation(summary = "Read Idaten adapter health", description = "delivery-service と registry-service に公開する現在の Idaten 可用状態を返します。")
    ResponseEntity<AdapterHealthResponse> health() {
        return ResponseEntity.ok(new AdapterHealthResponse("AVAILABLE", "idaten-adapter"));
    }
}