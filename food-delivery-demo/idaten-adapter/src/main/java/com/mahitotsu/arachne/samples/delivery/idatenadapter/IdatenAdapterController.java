package com.mahitotsu.arachne.samples.delivery.idatenadapter;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/adapter", produces = MediaType.APPLICATION_JSON_VALUE)
class IdatenAdapterController {

    private final IdatenAdapterService adapterService;

    IdatenAdapterController(IdatenAdapterService adapterService) {
        this.adapterService = adapterService;
    }

    @PostMapping("/eta")
    AdapterEtaResponse eta(@RequestBody AdapterEtaRequest request) {
        return adapterService.quote(request);
    }

    @GetMapping("/health")
    ResponseEntity<AdapterHealthResponse> health() {
        return ResponseEntity.ok(new AdapterHealthResponse("AVAILABLE", "idaten-adapter"));
    }
}