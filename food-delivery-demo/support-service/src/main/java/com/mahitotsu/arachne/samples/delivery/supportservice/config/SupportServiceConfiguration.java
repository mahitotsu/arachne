package com.mahitotsu.arachne.samples.delivery.supportservice.config;

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

@Configuration
@EnableConfigurationProperties(SupportServiceProperties.class)
class SupportServiceConfiguration {

    @Bean
    SecurityFilterChain supportSecurity(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {
                }))
                .build();
    }

    @Bean
    ApplicationRunner registerSupportService(
            RestClient.Builder restClientBuilder,
                        SupportServiceProperties properties) {
        return args -> {
                        String registryBaseUrl = properties.getRegistry().getBaseUrl();
            if (registryBaseUrl.isBlank()) {
                return;
            }
                        String serviceEndpoint = properties.getSupport().getEndpoint();
            restClientBuilder.baseUrl(registryBaseUrl).build().post()
                    .uri("/registry/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.ofEntries(
                            Map.entry("serviceName", "support-service"),
                            Map.entry("endpoint", serviceEndpoint),
                            Map.entry("capability", "FAQ回答、キャンペーン案内、問い合わせ受付、サービス稼働状況共有を扱う。"),
                            Map.entry("agentName", "support-agent"),
                            Map.entry("systemPrompt", "FAQ、キャンペーン、問い合わせ、稼働状況を整理し、必要なら注文履歴も参照して案内する。"),
                            Map.entry("skills", List.of(Map.of("name", "support-guide", "content", "FAQ、問い合わせ、キャンペーン、稼働状況のサポート導線"))),
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
}