package com.mahitotsu.arachne.samples.delivery.registryservice.config;

import static com.mahitotsu.arachne.samples.delivery.registryservice.domain.RegistryTypes.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

import com.mahitotsu.arachne.samples.delivery.registryservice.infrastructure.RegistryRepository;

@Configuration
@EnableConfigurationProperties(RegistryServiceProperties.class)
class RegistryBootstrapConfiguration {

    @Bean
    ApplicationRunner seedDefaults(
            RegistryRepository repository,
            RegistryServiceProperties properties,
            ResourceLoader resourceLoader) {
        return args -> {
            boolean seedDefaults = properties.isSeedDefaults();
            if (!seedDefaults) {
                return;
            }
            String registryEndpoint = properties.getEndpoint();
            List<SkillPayload> registrySkills = loadSkillsFromClasspath(resourceLoader, "capability-match");
            repository.register(new RegistryRegistration(
                    "registry-service",
                    registryEndpoint,
                    "サービスのケイパビリティ発見と稼働状況集約。自然言語クエリから適切な内部サービスやアダプターを案内する。",
                    "capability-registry-agent",
                    "capability_match を使って候補を確認し、返ってきた候補だけから最終 serviceName を絞り込む。未登録サービスは推測しない。",
                    registrySkills,
                    List.of(new SkillPayload("capability_match", "ケイパビリティ記述と問い合わせ文を照合して候補を返す")),
                    "POST",
                    "/registry/discover",
                    registryEndpoint + "/actuator/health",
                    AvailabilityStatus.AVAILABLE));
            repository.register(new RegistryRegistration(
                    "icarus-adapter",
                    "",
                    "外部ETAを提供するプレミアム配送パートナー。現時点では停止中のため候補からは除外する。",
                    "icarus-adapter",
                    "プレミアム配送 ETA を返す想定だが、現在は停止中。",
                    List.of(new SkillPayload("premium-eta", "プレミアム配送の ETA 見積もり")),
                    List.of(),
                    "POST",
                    "/adapter/eta",
                    "",
                    AvailabilityStatus.NOT_AVAILABLE));
        };
    }

    private static List<SkillPayload> loadSkillsFromClasspath(ResourceLoader loader, String... skillNames) {
        List<SkillPayload> result = new ArrayList<>();
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
                result.add(new SkillPayload(name, body));
            } catch (Exception e) {
                result.add(new SkillPayload(name, "(skill content not available)"));
            }
        }
        return result;
    }
}