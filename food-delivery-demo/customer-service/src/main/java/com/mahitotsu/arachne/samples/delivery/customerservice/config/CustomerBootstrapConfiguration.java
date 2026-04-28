package com.mahitotsu.arachne.samples.delivery.customerservice.config;

import java.util.List;
import java.util.Map;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestClient;

import com.mahitotsu.arachne.samples.delivery.customerservice.infrastructure.CustomerAccountRepository;

@Configuration
@EnableConfigurationProperties(CustomerServiceProperties.class)
class CustomerBootstrapConfiguration {

    @Bean
    ApplicationRunner demoCustomers(CustomerAccountRepository repository, PasswordEncoder passwordEncoder) {
        return args -> repository.seedDemoAccounts(passwordEncoder);
    }

    @Bean
    ApplicationRunner registerCustomerService(
            RestClient.Builder restClientBuilder,
            CustomerServiceProperties properties) {
        return args -> {
            String registryBaseUrl = properties.getRegistry().getBaseUrl();
            if (registryBaseUrl.isBlank()) {
                return;
            }
            String serviceEndpoint = properties.getCustomer().getEndpoint();
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