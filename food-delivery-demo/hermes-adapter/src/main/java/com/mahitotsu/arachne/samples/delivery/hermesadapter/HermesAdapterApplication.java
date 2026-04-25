package com.mahitotsu.arachne.samples.delivery.hermesadapter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

@SpringBootApplication
public class HermesAdapterApplication {

    public static void main(String[] args) {
        SpringApplication.run(HermesAdapterApplication.class, args);
    }

    @Bean
    ApplicationRunner registerHermesAdapter(
            RestClient.Builder restClientBuilder,
            @Value("${DELIVERY_REGISTRY_BASE_URL:}") String registryBaseUrl,
            @Value("${DELIVERY_HERMES_ENDPOINT:http://hermes-adapter:8080}") String adapterEndpoint) {
        return args -> {
            if (registryBaseUrl.isBlank()) {
                return;
            }
            restClientBuilder.baseUrl(registryBaseUrl).build().post()
                    .uri("/registry/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "serviceName", "hermes-adapter",
                            "endpoint", adapterEndpoint,
                            "capability", "外部ETAを提供する高速配送パートナー。混雑度と料金を返す。",
                            "agentName", "hermes-adapter",
                            "systemPrompt", "高速配送の ETA と混雑度を返す Hermes アダプター。",
                            "skills", List.of(Map.of("name", "partner-eta", "content", "高速配送の ETA と料金見積もり")),
                            "requestMethod", "POST",
                            "requestPath", "/adapter/eta",
                            "healthEndpoint", adapterEndpoint + "/adapter/health",
                            "status", "AVAILABLE"))
                    .retrieve()
                    .toBodilessEntity();
        };
    }
}

@RestController
@RequestMapping(path = "/adapter", produces = MediaType.APPLICATION_JSON_VALUE)
class HermesAdapterController {

    private final HermesAdapterService adapterService;

    HermesAdapterController(HermesAdapterService adapterService) {
        this.adapterService = adapterService;
    }

    @PostMapping("/eta")
    ResponseEntity<AdapterEtaResponse> eta(@RequestBody AdapterEtaRequest request) {
        AdapterEtaResponse response = adapterService.quote(request);
        return response.status().equals("AVAILABLE")
                ? ResponseEntity.ok(response)
                : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    @GetMapping("/health")
    ResponseEntity<AdapterHealthResponse> health() {
        boolean available = adapterService.available();
        AdapterHealthResponse response = new AdapterHealthResponse(available ? "AVAILABLE" : "NOT_AVAILABLE", "hermes-adapter");
        return available ? ResponseEntity.ok(response) : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }
}

@Service
class HermesAdapterService {

    boolean available() {
        return (System.currentTimeMillis() / 15000) % 3 != 0;
    }

    AdapterEtaResponse quote(AdapterEtaRequest request) {
        if (!available()) {
            return new AdapterEtaResponse("hermes-adapter", "NOT_AVAILABLE", 0, "high", new BigDecimal("0.00"),
                    "Hermes は現在混雑のため受付停止中です。");
        }
        int itemCount = request.itemNames() == null ? 0 : request.itemNames().size();
        return new AdapterEtaResponse(
                "hermes-adapter",
                "AVAILABLE",
                22 + itemCount,
                itemCount > 2 ? "high" : "medium",
                new BigDecimal("350.00"),
                "Hermes は高速配送を提供しています。");
    }
}

record AdapterEtaRequest(List<String> itemNames, String context) {}

record AdapterEtaResponse(String service, String status, int etaMinutes, String congestion, BigDecimal fee, String note) {}

record AdapterHealthResponse(String status, String service) {}