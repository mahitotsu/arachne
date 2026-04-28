package com.mahitotsu.arachne.samples.delivery.kitchenservice.config;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestClient;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
@EnableConfigurationProperties(KitchenServiceProperties.class)
class KitchenServiceConfiguration {

    @Bean
    SecurityFilterChain kitchenSecurity(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/info", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
                        .permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {
                }))
                .build();
    }

    @Bean
    OpenAPI kitchenServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Food Delivery Kitchen Service API")
                .version("v1")
                                .description("food-delivery-demo/docs/apis.md に対応した kitchen 在庫確認と代替判定のエンドポイントです。"));
    }

    @Bean
    ApplicationRunner registerKitchenService(
            RestClient.Builder restClientBuilder,
                        KitchenServiceProperties properties,
            ResourceLoader resourceLoader) {
        return args -> {
                        String registryBaseUrl = properties.getRegistry().getBaseUrl();
            if (registryBaseUrl.isBlank()) {
                return;
            }
                        String serviceEndpoint = properties.getKitchen().getEndpoint();
            List<Map<String, String>> skills = loadSkillsFromClasspath(resourceLoader,
                    "prep-scheduler", "substitution-approval");
            restClientBuilder.baseUrl(registryBaseUrl).build().post()
                    .uri("/registry/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.ofEntries(
                            Map.entry("serviceName", "kitchen-service"),
                            Map.entry("endpoint", serviceEndpoint),
                            Map.entry("capability", "在庫確認、調理ライン別 ETA 計算、欠品時の代替承認、混雑時の別ライン提案を扱う。"),
                            Map.entry("agentName", "kitchen-agent"),
                            Map.entry("systemPrompt", "kitchen_inventory_lookup を先に確認し、続けて prep_scheduler でライン状況を見る。欠品時だけ menu_substitution_lookup を使い、実際に提供できる代替品だけを承認する。"),
                            Map.entry("skills", skills),
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

        private static List<Map<String, String>> loadSkillsFromClasspath(ResourceLoader loader, String... skillNames) {
                List<Map<String, String>> result = new ArrayList<>();
                for (String name : skillNames) {
                        try {
                                var resource = loader.getResource("classpath:skills/" + name + "/SKILL.md");
                                String raw = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                                String body = raw;
                                if (raw.startsWith("---")) {
                                        int end = raw.indexOf("---", 3);
                                        if (end != -1) {
                                                body = raw.substring(end + 3).strip();
                                        }
                                }
                                result.add(Map.of("name", name, "content", body));
                        } catch (Exception e) {
                                result.add(Map.of("name", name, "content", "(skill content not available)"));
                        }
                }
                return result;
        }
}