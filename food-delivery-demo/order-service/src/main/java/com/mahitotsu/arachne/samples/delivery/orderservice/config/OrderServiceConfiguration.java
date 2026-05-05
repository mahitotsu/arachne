package com.mahitotsu.arachne.samples.delivery.orderservice.config;

import java.util.List;
import java.util.Map;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestClient;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
@EnableConfigurationProperties(OrderRegistryProperties.class)
class OrderServiceConfiguration {

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
                    .requestMatchers("/actuator/health", "/error", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> {
                }))
                .build();
    }

            @Bean
            OpenAPI orderServiceOpenApi() {
            return new OpenAPI().info(new Info()
                .title("Food Delivery Order Service API")
                .version("v1")
                .description("food-delivery-demo/docs/apis.md に対応した注文ワークフロー service の code-first OpenAPI です。"));
            }

    @Bean
    ApplicationRunner registerOrderService(
            RestClient.Builder restClientBuilder,
            OrderRegistryProperties properties) {
        return args -> {
            String registryBaseUrl = properties.getRegistry().getBaseUrl();
            if (registryBaseUrl.isBlank()) {
                return;
            }
            String serviceEndpoint = properties.getOrder().getEndpoint();
            restClientBuilder.baseUrl(registryBaseUrl).build().post()
                    .uri("/registry/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "serviceName", "order-service",
                            "endpoint", serviceEndpoint,
                            "capability", "注文ワークフロー管理、注文意図の正規化、メニュー grounding 起動、配送選択、支払い確認、注文履歴参照を扱う。",
                            "agentName", "order-intake-agent",
                            "systemPrompt", "注文意図を正規化し、menu-service への catalog grounding を起動する。",
                            "skills", List.of(
                                Map.of("name", "order-intake", "content", "customer の注文意図を正規化する"),
                                Map.of("name", "order-workflow", "content", "注文の提案から確定までの段階進行")),
                            "requestMethod", "POST",
                            "requestPath", "/api/order/suggest",
                            "healthEndpoint", serviceEndpoint + "/actuator/health",
                            "status", "AVAILABLE"))
                    .retrieve()
                    .toBodilessEntity();
        };
    }
}