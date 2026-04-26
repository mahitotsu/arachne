package com.mahitotsu.arachne.samples.delivery.customerservice;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestClient;

@Configuration
class CustomerBootstrapConfiguration {

    @Bean
    ApplicationRunner demoCustomers(CustomerAccountRepository repository, PasswordEncoder passwordEncoder) {
        return args -> repository.seedDemoAccounts(passwordEncoder);
    }

    @Bean
    ApplicationRunner registerCustomerService(
            RestClient.Builder restClientBuilder,
            @Value("${DELIVERY_REGISTRY_BASE_URL:}") String registryBaseUrl,
            @Value("${DELIVERY_CUSTOMER_ENDPOINT:http://customer-service:8080}") String serviceEndpoint) {
        return args -> {
            if (registryBaseUrl.isBlank()) {
                return;
            }
            restClientBuilder.baseUrl(registryBaseUrl).build().post()
                    .uri("/registry/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "serviceName", "customer-service",
                            "endpoint", serviceEndpoint,
                            "capability", "顧客認証、JWT 発行、プロフィール取得を扱う。",
                            "agentName", "customer-service",
                            "systemPrompt", "デモ顧客の認証とプロフィール参照を提供する。",
                            "skills", List.of(Map.of("name", "customer-auth", "content", "サインインとプロフィール取得")),
                            "requestMethod", "POST",
                            "requestPath", "/api/auth/sign-in",
                            "healthEndpoint", serviceEndpoint + "/actuator/health",
                            "status", "AVAILABLE"))
                    .retrieve()
                    .toBodilessEntity();
        };
    }
}