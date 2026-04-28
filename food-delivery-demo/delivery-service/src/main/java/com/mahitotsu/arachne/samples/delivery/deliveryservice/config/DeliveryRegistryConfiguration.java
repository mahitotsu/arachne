package com.mahitotsu.arachne.samples.delivery.deliveryservice.config;

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
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(DeliveryServiceProperties.class)
class DeliveryRegistryConfiguration {

    @Bean
    ApplicationRunner registerDeliveryService(
            RestClient.Builder restClientBuilder,
            DeliveryServiceProperties properties,
            ResourceLoader resourceLoader) {
        return args -> {
            String registryBaseUrl = properties.getRegistry().getBaseUrl();
            if (registryBaseUrl.isBlank()) {
                return;
            }
            String serviceEndpoint = properties.getDelivery().getEndpoint();
            List<Map<String, String>> skills = loadSkillsFromClasspath(resourceLoader, "delivery-routing");
            restClientBuilder.baseUrl(registryBaseUrl).build().post()
                    .uri("/registry/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.ofEntries(
                            Map.entry("serviceName", "delivery-service"),
                            Map.entry("endpoint", serviceEndpoint),
                            Map.entry("capability", "自社スタッフ確認、天候・交通考慮、registry discovery、外部 ETA 比較、配送候補の推奨を扱う。"),
                            Map.entry("agentName", "delivery-agent"),
                            Map.entry("systemPrompt", "delivery-routing を使い、自社確認→交通・天候→registry discovery→各 AVAILABLE 外部 ETA 呼び出しの順で比較する。存在しない候補や未確認の可用性を推測しない。"),
                            Map.entry("skills", skills),
                            Map.entry("tools", List.of(
                                    Map.of("name", "check_courier_availability", "content", "自社配送スタッフの可用性を確認する"),
                                    Map.of("name", "get_traffic_weather", "content", "交通・天候情報を取得し ETA 調整に使用する"),
                                    Map.of("name", "discover_eta_services", "content", "外部 ETA 提供サービスを発見する"),
                                    Map.of("name", "call_eta_service", "content", "外部 ETA サービスを呼び出す"))),
                            Map.entry("requestMethod", "POST"),
                            Map.entry("requestPath", "/internal/delivery/quote"),
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