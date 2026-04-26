package com.mahitotsu.arachne.samples.delivery.deliveryservice;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Configuration
class DeliveryRegistryConfiguration {

    @Bean
    ApplicationRunner registerDeliveryService(
            RestClient.Builder restClientBuilder,
            @Value("${DELIVERY_REGISTRY_BASE_URL:}") String registryBaseUrl,
            @Value("${DELIVERY_DELIVERY_ENDPOINT:http://delivery-service:8080}") String serviceEndpoint) {
        return args -> {
            if (registryBaseUrl.isBlank()) {
                return;
            }
            restClientBuilder.baseUrl(registryBaseUrl).build().post()
                    .uri("/registry/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "serviceName", "delivery-service",
                            "endpoint", serviceEndpoint,
                            "capability", "配送見積もり、自社スタッフの可用性確認、交通と天候を踏まえた ETA 評価を扱う。",
                            "agentName", "delivery-agent",
                            "systemPrompt", "配送レーンの可用性と ETA を比較して要約する。",
                            "skills", List.of(Map.of("name", "delivery-routing", "content", "配送レーン比較と ETA 評価")),
                            "requestMethod", "POST",
                            "requestPath", "/internal/delivery/quote",
                            "healthEndpoint", serviceEndpoint + "/actuator/health",
                            "status", "AVAILABLE"))
                    .retrieve()
                    .toBodilessEntity();
        };
    }
}