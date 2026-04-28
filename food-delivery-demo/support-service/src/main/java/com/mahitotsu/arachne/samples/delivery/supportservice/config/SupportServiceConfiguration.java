package com.mahitotsu.arachne.samples.delivery.supportservice.config;

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
@EnableConfigurationProperties(SupportServiceProperties.class)
class SupportServiceConfiguration {

    @Bean
    SecurityFilterChain supportSecurity(HttpSecurity http) throws Exception {
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
    OpenAPI supportServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Food Delivery Support Service API")
                .version("v1")
                .description("Support chat, feedback, campaigns, and service-status endpoints aligned with food-delivery-demo/docs/apis.md."));
    }

    @Bean
    ApplicationRunner registerSupportService(
            RestClient.Builder restClientBuilder,
                        SupportServiceProperties properties,
            ResourceLoader resourceLoader) {
        return args -> {
                        String registryBaseUrl = properties.getRegistry().getBaseUrl();
            if (registryBaseUrl.isBlank()) {
                return;
            }
                        String serviceEndpoint = properties.getSupport().getEndpoint();
            List<Map<String, String>> skills = loadSkillsFromClasspath(resourceLoader,
                    "support-guide", "order-handoff-boundary");
            restClientBuilder.baseUrl(registryBaseUrl).build().post()
                    .uri("/registry/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.ofEntries(
                            Map.entry("serviceName", "support-service"),
                            Map.entry("endpoint", serviceEndpoint),
                            Map.entry("capability", "FAQ回答、キャンペーン案内、サービス稼働状況共有、注文履歴参照、order-service へのハンドオフ判断を扱う。"),
                            Map.entry("agentName", "support-agent"),
                            Map.entry("systemPrompt", "support-guide を前提に必要なツールだけを使い、order-service の責務に入る相談だけを handoffTarget=order へ送る。返金確約や注文変更の実行は行わない。"),
                            Map.entry("skills", skills),
                            Map.entry("tools", List.of(
                                    Map.of("name", "faq_lookup", "content", "FAQナレッジを検索して回答候補を返す"),
                                    Map.of("name", "campaign_lookup", "content", "現在有効なキャンペーンを返す"),
                                    Map.of("name", "service_status_lookup", "content", "registry-service 経由で現在の稼働状況を取得する"),
                                    Map.of("name", "feedback_lookup", "content", "過去の問い合わせ・フィードバックを検索する"),
                                    Map.of("name", "order_history_lookup", "content", "認証済みカスタマーの直近注文履歴を取得する"))),
                            Map.entry("requestMethod", "POST"),
                            Map.entry("requestPath", "/api/support/chat"),
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