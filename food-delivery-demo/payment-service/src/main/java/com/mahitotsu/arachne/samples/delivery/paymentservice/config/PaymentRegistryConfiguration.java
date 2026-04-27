package com.mahitotsu.arachne.samples.delivery.paymentservice.config;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Configuration
class PaymentRegistryConfiguration {

    @Bean
    ApplicationRunner registerPaymentService(
            RestClient.Builder restClientBuilder,
            @Value("${DELIVERY_REGISTRY_BASE_URL:}") String registryBaseUrl,
            @Value("${DELIVERY_PAYMENT_ENDPOINT:http://payment-service:8080}") String serviceEndpoint) {
        return args -> {
            if (registryBaseUrl.isBlank()) {
                return;
            }
            restClientBuilder.baseUrl(registryBaseUrl).build().post()
                    .uri("/registry/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "serviceName", "payment-service",
                            "endpoint", serviceEndpoint,
                            "capability", "支払い手段の提案、支払い準備、請求確定を扱う。",
                            "agentName", "payment-service",
                            "systemPrompt", "決定的な支払い手段選択と請求処理を提供する。",
                            "skills", List.of(Map.of("name", "payment-profile", "content", "支払いプロファイルの選択と請求確定")),
                            "requestMethod", "POST",
                            "requestPath", "/internal/payment/prepare",
                            "healthEndpoint", serviceEndpoint + "/actuator/health",
                            "status", "AVAILABLE"))
                    .retrieve()
                    .toBodilessEntity();
        };
    }
}