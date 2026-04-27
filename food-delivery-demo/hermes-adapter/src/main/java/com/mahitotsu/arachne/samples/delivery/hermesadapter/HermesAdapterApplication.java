package com.mahitotsu.arachne.samples.delivery.hermesadapter;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
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