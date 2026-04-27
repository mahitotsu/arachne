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
                    .body(Map.ofEntries(
                            Map.entry("serviceName", "delivery-service"),
                            Map.entry("endpoint", serviceEndpoint),
                            Map.entry("capability", "配送見積もり、自社スタッフの可用性確認、交通と天候を踏まえた ETA 評価を扱う。"),
                            Map.entry("agentName", "delivery-agent"),
                            Map.entry("systemPrompt", "配送レーンの可用性と ETA を比較して要約する。"),
                            Map.entry("skills", List.of(Map.of("name", "delivery-routing", "content", "配送レーン比較と ETA 評価"))),
                            Map.entry("tools", List.of(
                                    Map.of("name", "check_courier_availability", "content", "自社配送スタッフの可用性を確認する"),
                                    Map.of("name", "get_traffic_weather", "content", "交通・天候情報を取得し ETA 調整に使用する"),
                                    Map.of("name", "discover_eta_services", "content", "外部 ETA 提供サービスを発見する"),
                                    Map.of("name", "call_eta_service", "content", "外部 ETA サービスを呼び出す"))),
                            Map.entry("requestMethod", "POST"),
                            Map.entry("requestPath", "/internal/delivery/quote"),
                            Map.entry("healthEndpoint", serviceEndpoint + "/actuator/health"),
                            Map.entry("status", "AVAILABLE")))
                    .retrieve()
                    .toBodilessEntity();
        };
    }
}