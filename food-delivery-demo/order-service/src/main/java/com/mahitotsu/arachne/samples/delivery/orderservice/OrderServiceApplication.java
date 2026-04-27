package com.mahitotsu.arachne.samples.delivery.orderservice;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestClient;

@SpringBootApplication
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }

    @Bean
    SecurityFilterChain orderSecurity(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> {
                }))
                .build();
    }

    @Bean
    ApplicationRunner registerOrderService(
            RestClient.Builder restClientBuilder,
            @Value("${DELIVERY_REGISTRY_BASE_URL:}") String registryBaseUrl,
            @Value("${DELIVERY_ORDER_ENDPOINT:http://order-service:8080}") String serviceEndpoint) {
        return args -> {
            if (registryBaseUrl.isBlank()) {
                return;
            }
            restClientBuilder.baseUrl(registryBaseUrl).build().post()
                    .uri("/registry/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "serviceName", "order-service",
                            "endpoint", serviceEndpoint,
                            "capability", "注文ワークフロー管理、メニュー提案、配送選択、支払い確認、注文履歴参照を扱う。",
                            "agentName", "order-service",
                            "systemPrompt", "注文ワークフローを段階的に進める。",
                            "skills", List.of(Map.of("name", "order-workflow", "content", "注文の提案から確定までの段階進行")),
                            "requestMethod", "POST",
                            "requestPath", "/api/order/suggest",
                            "healthEndpoint", serviceEndpoint + "/actuator/health",
                            "status", "AVAILABLE"))
                    .retrieve()
                    .toBodilessEntity();
        };
    }
}