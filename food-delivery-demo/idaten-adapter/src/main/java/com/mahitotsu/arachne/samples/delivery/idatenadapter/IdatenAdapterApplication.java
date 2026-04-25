package com.mahitotsu.arachne.samples.delivery.idatenadapter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
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
public class IdatenAdapterApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdatenAdapterApplication.class, args);
    }

    @Bean
    ApplicationRunner registerIdatenAdapter(
            RestClient.Builder restClientBuilder,
            @Value("${DELIVERY_REGISTRY_BASE_URL:}") String registryBaseUrl,
            @Value("${DELIVERY_IDATEN_ENDPOINT:http://idaten-adapter:8080}") String adapterEndpoint) {
        return args -> {
            if (registryBaseUrl.isBlank()) {
                return;
            }
            restClientBuilder.baseUrl(registryBaseUrl).build().post()
                    .uri("/registry/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "serviceName", "idaten-adapter",
                            "endpoint", adapterEndpoint,
                            "capability", "外部ETAを提供する低コスト配送パートナー。常時利用可能で料金優先の配送候補を返す。",
                            "agentName", "idaten-adapter",
                            "systemPrompt", "低コスト配送の ETA と料金を返す Idaten アダプター。",
                            "skills", List.of(Map.of("name", "partner-eta", "content", "低コスト配送の ETA と料金見積もり")),
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

@Service
class IdatenAdapterService {

    AdapterEtaResponse quote(AdapterEtaRequest request) {
        int itemCount = request.itemNames() == null ? 0 : request.itemNames().size();
        return new AdapterEtaResponse(
                "idaten-adapter",
                "AVAILABLE",
                34 + itemCount,
                "low",
                new BigDecimal("180.00"),
                "Idaten は低価格配送を提供しています。");
    }
}

record AdapterEtaRequest(List<String> itemNames, String context) {}

record AdapterEtaResponse(String service, String status, int etaMinutes, String congestion, BigDecimal fee, String note) {}

record AdapterHealthResponse(String status, String service) {}