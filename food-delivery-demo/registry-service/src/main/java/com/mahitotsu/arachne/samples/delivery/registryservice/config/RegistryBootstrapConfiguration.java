package com.mahitotsu.arachne.samples.delivery.registryservice.config;

import static com.mahitotsu.arachne.samples.delivery.registryservice.domain.RegistryTypes.*;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.mahitotsu.arachne.samples.delivery.registryservice.infrastructure.RegistryRepository;

@Configuration
class RegistryBootstrapConfiguration {

    @Bean
    ApplicationRunner seedDefaults(
            RegistryRepository repository,
            @Value("${delivery.registry.seed-defaults:true}") boolean seedDefaults,
            @Value("${delivery.registry.endpoint:http://registry-service:8080}") String registryEndpoint) {
        return args -> {
            if (!seedDefaults) {
                return;
            }
            repository.register(new RegistryRegistration(
                    "registry-service",
                    registryEndpoint,
                    "サービスのケイパビリティ発見と稼働状況集約。自然言語クエリから適切な内部サービスやアダプターを案内する。",
                    "capability-registry-agent",
                    "登録済みサービスのケイパビリティと稼働状況を照合し、利用可能な候補を返す。",
                    List.of(new SkillPayload("capability-match", "ケイパビリティ記述と問い合わせ文を照合して候補を返す")),
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
}