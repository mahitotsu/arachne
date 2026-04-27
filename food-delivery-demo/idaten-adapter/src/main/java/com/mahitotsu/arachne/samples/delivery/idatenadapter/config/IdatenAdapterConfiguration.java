package com.mahitotsu.arachne.samples.delivery.idatenadapter.config;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Configuration
class IdatenAdapterConfiguration {

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