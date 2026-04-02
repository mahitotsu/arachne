package com.mahitotsu.arachne.samples.marketplace.workflowservice;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mahitotsu.arachne.strands.model.Model;
import com.mahitotsu.arachne.strands.session.InMemorySessionManager;
import com.mahitotsu.arachne.strands.session.SessionManager;
import com.mahitotsu.arachne.strands.skills.Skill;
import com.mahitotsu.arachne.strands.skills.SkillParser;
import com.mahitotsu.arachne.strands.spring.AgentFactory;
import com.mahitotsu.arachne.strands.tool.ExecutionContextPropagation;

@Configuration
@EnableConfigurationProperties({
    WorkflowServiceConfiguration.DownstreamProperties.class,
    WorkflowServiceConfiguration.WorkflowSessionProperties.class
})
class WorkflowServiceConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    @Primary
    ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Bean
    RestClient escrowRestClient(RestClient.Builder builder, DownstreamProperties properties) {
        return builder.baseUrl(properties.escrowBaseUrl()).build();
    }

    @Bean
    RestClient shipmentRestClient(RestClient.Builder builder, DownstreamProperties properties) {
        return builder.baseUrl(properties.shipmentBaseUrl()).build();
    }

    @Bean
    RestClient riskRestClient(RestClient.Builder builder, DownstreamProperties properties) {
        return builder.baseUrl(properties.riskBaseUrl()).build();
    }

    @Bean
    RestClient notificationRestClient(RestClient.Builder builder, DownstreamProperties properties) {
        return builder.baseUrl(properties.notificationBaseUrl()).build();
    }

    @Bean(name = "arachneDiscoveredSkills")
    List<Skill> arachneDiscoveredSkills() {
        return List.of();
    }

    @Bean(name = "caseWorkflowAgentSkills")
    List<Skill> caseWorkflowAgentSkills(SkillParser skillParser) {
        return List.of(
                parseSkill(skillParser, "skills/marketplace-dispute-intake/SKILL.md"),
                parseSkill(skillParser, "skills/item-not-received-investigation/SKILL.md"),
                parseSkill(skillParser, "skills/approval-escalation-and-resume/SKILL.md"));
    }

    @Bean(name = "shipmentAgentSkills")
    List<Skill> shipmentAgentSkills(SkillParser skillParser) {
        return List.of(parseSkill(skillParser, "skills/shipment-evidence-review/SKILL.md"));
    }

    @Bean(name = "escrowAgentSkills")
    List<Skill> escrowAgentSkills(SkillParser skillParser) {
        return List.of(parseSkill(skillParser, "skills/settlement-eligibility-summary/SKILL.md"));
    }

    @Bean(name = "riskAgentSkills")
    List<Skill> riskAgentSkills(SkillParser skillParser) {
        return List.of(parseSkill(skillParser, "skills/risk-review-summary/SKILL.md"));
    }

    @Bean
    MarketplaceFinanceControlApprovalPlugin marketplaceFinanceControlApprovalPlugin() {
        return new MarketplaceFinanceControlApprovalPlugin();
    }

    @Bean
    MarketplaceSettlementShortcutSteering marketplaceSettlementShortcutSteering() {
        return new MarketplaceSettlementShortcutSteering();
    }

    @Bean
    OperatorAuthorizationContextHolder operatorAuthorizationContextHolder() {
        return new OperatorAuthorizationContextHolder();
    }

    @Bean
    ExecutionContextPropagation operatorAuthorizationContextPropagation(
            OperatorAuthorizationContextHolder operatorAuthorizationContextHolder) {
        return task -> {
            OperatorAuthorizationContext captured = operatorAuthorizationContextHolder.current();
            return () -> {
                OperatorAuthorizationContext previous = operatorAuthorizationContextHolder.current();
                operatorAuthorizationContextHolder.restore(captured);
                try {
                    task.run();
                } finally {
                    operatorAuthorizationContextHolder.restore(previous);
                }
            };
        };
    }

    @Bean
    MarketplaceOperatorContextPlugin marketplaceOperatorContextPlugin(
            OperatorAuthorizationContextHolder operatorAuthorizationContextHolder) {
        return new MarketplaceOperatorContextPlugin(operatorAuthorizationContextHolder);
    }

    @Bean
    @ConditionalOnProperty(prefix = "workflow-session", name = "store", havingValue = "memory", matchIfMissing = true)
    SessionManager workflowArachneInMemorySessionManager() {
        return new InMemorySessionManager();
    }

    @Bean
    @ConditionalOnProperty(prefix = "workflow-session", name = "store", havingValue = "redis")
    SessionManager workflowArachneRedisSessionManager(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            WorkflowSessionProperties properties) {
        return new RedisWorkflowArachneSessionManager(redisTemplate, objectMapper, properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "marketplace.workflow-runtime.arachne", name = "model-mode", havingValue = "deterministic", matchIfMissing = true)
    Model marketplaceWorkflowDeterministicModel() {
        return new MarketplaceWorkflowArachneModel();
    }

    @Bean
    @ConditionalOnProperty(prefix = "marketplace.workflow-runtime.arachne", name = "enabled", havingValue = "true")
    WorkflowRuntimeAdapter arachneWorkflowRuntimeAdapter(
            AgentFactory agentFactory,
            @Qualifier("caseWorkflowAgentSkills") List<Skill> caseWorkflowSkills,
            @Qualifier("shipmentAgentSkills") List<Skill> shipmentSkills,
            @Qualifier("escrowAgentSkills") List<Skill> escrowSkills,
            @Qualifier("riskAgentSkills") List<Skill> riskSkills,
            MarketplaceOperatorContextPlugin marketplaceOperatorContextPlugin,
            MarketplaceFinanceControlApprovalPlugin marketplaceFinanceControlApprovalPlugin,
            MarketplaceSettlementShortcutSteering marketplaceSettlementShortcutSteering) {
        return new ArachneWorkflowRuntimeAdapter(
                agentFactory,
                caseWorkflowSkills,
                shipmentSkills,
                escrowSkills,
                riskSkills,
                marketplaceOperatorContextPlugin,
                marketplaceFinanceControlApprovalPlugin,
                marketplaceSettlementShortcutSteering);
    }

    @Bean
    @ConditionalOnMissingBean(WorkflowRuntimeAdapter.class)
    WorkflowRuntimeAdapter deterministicWorkflowRuntimeAdapter() {
        return new DeterministicWorkflowRuntimeAdapter();
    }

    private Skill parseSkill(SkillParser skillParser, String path) {
        return skillParser.parse(new ClassPathResource(path));
    }

    @ConfigurationProperties(prefix = "downstream")
    record DownstreamProperties(
            String escrowBaseUrl,
            String shipmentBaseUrl,
            String riskBaseUrl,
            String notificationBaseUrl) {
    }

    @ConfigurationProperties(prefix = "workflow-session")
    record WorkflowSessionProperties(
            String store,
            String keyPrefix,
            Duration ttl) {
    }
}