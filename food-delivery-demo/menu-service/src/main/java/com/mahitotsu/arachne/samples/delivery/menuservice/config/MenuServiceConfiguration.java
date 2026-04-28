package com.mahitotsu.arachne.samples.delivery.menuservice.config;

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

@Configuration
@EnableConfigurationProperties(MenuServiceProperties.class)
class MenuServiceConfiguration {

    @Bean
    SecurityFilterChain menuSecurity(HttpSecurity http) throws Exception {
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
    ApplicationRunner registerMenuService(
            RestClient.Builder restClientBuilder,
                        MenuServiceProperties properties,
            ResourceLoader resourceLoader) {
        return args -> {
                        String registryBaseUrl = properties.getRegistry().getBaseUrl();
            if (registryBaseUrl.isBlank()) {
                return;
            }
                        String serviceEndpoint = properties.getMenu().getEndpoint();
            List<Map<String, String>> skills = loadSkillsFromClasspath(resourceLoader,
                    "proactive-recommendation", "family-order-guide", "substitution-support-boundary");
            restClientBuilder.baseUrl(registryBaseUrl).build().post()
                    .uri("/registry/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.ofEntries(
                            Map.entry("serviceName", "menu-service"),
                            Map.entry("endpoint", serviceEndpoint),
                        Map.entry("capability", "メニュー提案、カタログ根拠に基づくおすすめ、家族向け数量調整、欠品時の代替候補準備、合計金額計算を扱う。"),
                            Map.entry("agentName", "menu-agent"),
                        Map.entry("systemPrompt", "必要に応じて menu 系スキルを有効化しつつ、catalog_lookup_tool を先に使って現在のカタログを確認し、最後に calculate_total_tool で検算する。欠品・提供可否・ETA の最終判断は kitchen-service に委ねる。"),
                            Map.entry("skills", skills),
                            Map.entry("tools", List.of(
                                    Map.of("name", "catalog_lookup_tool", "content", "ローカルメニューカタログを参照し、候補一覧を返す"),
                                    Map.of("name", "calculate_total_tool", "content", "候補セットの合計金額を計算する"),
                                    Map.of("name", "menu_substitution_lookup", "content", "欠品時に代替候補を準備する"))),
                            Map.entry("requestMethod", "POST"),
                            Map.entry("requestPath", "/internal/menu/suggest"),
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