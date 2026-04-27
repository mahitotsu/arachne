package com.mahitotsu.arachne.samples.delivery.kitchenservice;

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
public class KitchenServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(KitchenServiceApplication.class, args);
    }

    @Bean
    SecurityFilterChain kitchenSecurity(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
                .build();
    }

    @Bean
    ApplicationRunner registerKitchenService(
            RestClient.Builder restClientBuilder,
            @Value("${DELIVERY_REGISTRY_BASE_URL:}") String registryBaseUrl,
            @Value("${DELIVERY_KITCHEN_ENDPOINT:http://kitchen-service:8080}") String serviceEndpoint) {
        return args -> {
            if (registryBaseUrl.isBlank()) {
                return;
            }
            restClientBuilder.baseUrl(registryBaseUrl).build().post()
                    .uri("/registry/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.ofEntries(
                            Map.entry("serviceName", "kitchen-service"),
                            Map.entry("endpoint", serviceEndpoint),
                            Map.entry("capability", "在庫確認、調理 ETA 推定、調理ライン混雑に応じた代替提案を扱う。"),
                            Map.entry("agentName", "kitchen-agent"),
                            Map.entry("systemPrompt", "在庫と調理ラインの状況を確認し、必要に応じて代替調理ラインを提案する。"),
                            Map.entry("skills", List.of(Map.of("name", "prep-scheduler", "content", "調理 ETA と混雑時の代替ライン提案"))),
                            Map.entry("tools", List.of(
                                    Map.of("name", "menu_substitution_lookup", "content", "利用不可アイテムの代替候補を協業先に問い合わせる"),
                                    Map.of("name", "kitchen_inventory_lookup", "content", "選択アイテムの在庫プレッシャーと調理時間を確認する"),
                                    Map.of("name", "prep_scheduler", "content", "調理ラインごとのキュー遅延と提供見込み時間を計算する"))),
                            Map.entry("requestMethod", "POST"),
                            Map.entry("requestPath", "/internal/kitchen/check"),
                            Map.entry("healthEndpoint", serviceEndpoint + "/actuator/health"),
                            Map.entry("status", "AVAILABLE")))
                    .retrieve()
                    .toBodilessEntity();
        };
    }
}