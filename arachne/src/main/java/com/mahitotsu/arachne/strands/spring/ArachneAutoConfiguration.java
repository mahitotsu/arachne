package com.mahitotsu.arachne.strands.spring;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.session.MapSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.mahitotsu.arachne.strands.hooks.HookProvider;
import com.mahitotsu.arachne.strands.model.Model;
import com.mahitotsu.arachne.strands.model.retry.ExponentialBackoffRetryStrategy;
import com.mahitotsu.arachne.strands.model.retry.ModelRetryStrategy;
import com.mahitotsu.arachne.strands.session.FileSessionManager;
import com.mahitotsu.arachne.strands.session.SessionManager;
import com.mahitotsu.arachne.strands.session.SpringSessionManager;
import com.mahitotsu.arachne.strands.skills.Skill;
import com.mahitotsu.arachne.strands.skills.SkillParser;
import com.mahitotsu.arachne.strands.tool.BeanValidationSupport;
import com.mahitotsu.arachne.strands.tool.ExecutionContextPropagation;
import com.mahitotsu.arachne.strands.tool.annotation.DiscoveredTool;
import com.mahitotsu.arachne.strands.tool.builtin.BuiltInResourceAccessPolicy;
import com.mahitotsu.arachne.strands.tool.builtin.CalculatorTool;
import com.mahitotsu.arachne.strands.tool.builtin.CurrentTimeTool;
import com.mahitotsu.arachne.strands.tool.builtin.ResourceListTool;
import com.mahitotsu.arachne.strands.tool.builtin.ResourceReaderTool;
import jakarta.validation.Validator;

/**
 * Spring Boot auto-configuration for Arachne Strands.
 *
 * <p>Activated automatically via {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 * Users get an {@code AgentFactory} bean by default; wire in a {@code Model} bean to change provider.
 */
@AutoConfiguration
@EnableConfigurationProperties(ArachneProperties.class)
public class ArachneAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    @ConditionalOnMissingBean
    public Model arachneModel(ArachneProperties properties) {
        return AgentFactory.createDefaultModel(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public Validator arachneValidator() {
        return BeanValidationSupport.defaultValidator();
    }

    @Bean
    @ConditionalOnMissingBean
    public com.mahitotsu.arachne.strands.tool.annotation.AnnotationToolScanner annotationToolScanner(
            ObjectMapper objectMapper,
            Validator validator) {
        return new com.mahitotsu.arachne.strands.tool.annotation.AnnotationToolScanner(
                new com.mahitotsu.arachne.strands.schema.JsonSchemaGenerator(objectMapper),
                objectMapper,
                validator);
    }

    @Bean(name = "arachneDiscoveredTools")
    @ConditionalOnMissingBean(name = "arachneDiscoveredTools")
    public List<DiscoveredTool> arachneDiscoveredTools(ApplicationContext applicationContext,
                                             com.mahitotsu.arachne.strands.tool.annotation.AnnotationToolScanner annotationToolScanner) {
        return annotationToolScanner.scanDiscoveredTools(applicationContext.getBeansOfType(Object.class).values());
    }

    @Bean(name = "arachneDiscoveredHooks")
    @ConditionalOnMissingBean(name = "arachneDiscoveredHooks")
    public List<HookProvider> arachneDiscoveredHooks(ApplicationContext applicationContext) {
        return applicationContext.getBeansWithAnnotation(ArachneHook.class).values().stream()
                .filter(HookProvider.class::isInstance)
                .map(HookProvider.class::cast)
                .toList();
    }

    @Bean
    @ConditionalOnMissingBean
    public SkillParser skillParser() {
        return new SkillParser();
    }

    @Bean
    @ConditionalOnMissingBean
    public ResourcePatternResolver arachneResourcePatternResolver() {
        return new PathMatchingResourcePatternResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    public ArachneTemplateRenderer arachneTemplateRenderer(
            ResourcePatternResolver resourcePatternResolver,
            ObjectMapper objectMapper) {
        return new ArachneTemplateRenderer(resourcePatternResolver, objectMapper);
    }

    @Bean(name = "arachneBuiltInTools")
    @ConditionalOnMissingBean(name = "arachneBuiltInTools")
    public BuiltInToolRegistry arachneBuiltInTools(
            ArachneProperties properties,
            ResourcePatternResolver resourcePatternResolver) {
        ArachneProperties.ResourceAccessProperties resourceAccess = properties.getAgent().getBuiltIns().getResources();
        BuiltInResourceAccessPolicy accessPolicy = new BuiltInResourceAccessPolicy(
                resourceAccess.getAllowedClasspathLocations(),
                resourceAccess.getAllowedFileLocations());
        return new BuiltInToolRegistry(List.of(
            new BuiltInToolDefinition(new CalculatorTool(), true, Set.of("read-only", "utility")),
                new BuiltInToolDefinition(new CurrentTimeTool(), true, Set.of("read-only", "utility")),
                new BuiltInToolDefinition(new ResourceReaderTool(resourcePatternResolver, accessPolicy), true, Set.of("read-only", "resource")),
            new BuiltInToolDefinition(new ResourceListTool(resourcePatternResolver, accessPolicy), true, Set.of("read-only", "resource"))));
    }

    @Bean(name = "arachneDiscoveredSkills")
    @ConditionalOnMissingBean(name = "arachneDiscoveredSkills")
    public List<Skill> arachneDiscoveredSkills(SkillParser skillParser) {
        return new ClasspathSkillDiscoverer(skillParser).discover();
    }

    @Bean
    @ConditionalOnMissingBean
    public ApplicationEventPublishingHookProvider arachneApplicationEventHookProvider(
            ApplicationEventPublisher applicationEventPublisher) {
        return new ApplicationEventPublishingHookProvider(applicationEventPublisher);
    }

    @Bean
    @ConditionalOnMissingBean
    public SessionManager sessionManager(
            ArachneProperties properties,
            ObjectProvider<SessionRepository<?>> sessionRepositoryProvider,
            ObjectMapper objectMapper) {
        String directory = properties.getAgent().getSession().getFile().getDirectory();
        if (directory != null && !directory.isBlank()) {
            return new FileSessionManager(Path.of(directory), objectMapper);
        }
        SessionRepository<?> sessionRepository = sessionRepositoryProvider.getIfAvailable();
        if (sessionRepository != null) {
            return new SpringSessionManager(asSessionRepository(sessionRepository), objectMapper);
        }
        return new SpringSessionManager(new MapSessionRepository(new ConcurrentHashMap<>()), objectMapper);
    }

    @Bean(name = "arachneToolExecutionExecutor", destroyMethod = "close")
    @ConditionalOnMissingBean(name = "arachneToolExecutionExecutor")
    public ExecutorService arachneToolExecutionExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "arachne.strands.agent.retry", name = "enabled", havingValue = "true")
    public ModelRetryStrategy modelRetryStrategy(ArachneProperties properties) {
        ArachneProperties.RetryProperties retry = properties.getAgent().getRetry();
        return new ExponentialBackoffRetryStrategy(
                retry.getMaxAttempts(),
                retry.getInitialDelay(),
                retry.getMaxDelay());
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentFactory agentFactory(
            ArachneProperties properties,
            Model model,
            @Qualifier("arachneBuiltInTools") BuiltInToolRegistry arachneBuiltInTools,
            List<DiscoveredTool> arachneDiscoveredTools,
            @Qualifier("arachneDiscoveredHooks") List<HookProvider> arachneDiscoveredHooks,
            @Qualifier("arachneDiscoveredSkills") List<Skill> arachneDiscoveredSkills,
            Validator validator,
            SessionManager sessionManager,
            ObjectProvider<ModelRetryStrategy> modelRetryStrategyProvider,
            ObjectProvider<ExecutionContextPropagation> executionContextPropagationProvider,
            ObjectMapper objectMapper,
            @Qualifier("arachneToolExecutionExecutor") Executor toolExecutionExecutor) {
        return new AgentFactory(
                properties,
                model,
                arachneBuiltInTools,
                arachneDiscoveredTools,
                arachneDiscoveredHooks,
                arachneDiscoveredSkills,
                validator,
                sessionManager,
                modelRetryStrategyProvider.getIfAvailable(),
                objectMapper,
                toolExecutionExecutor,
                ExecutionContextPropagation.compose(executionContextPropagationProvider.orderedStream().toList()));
    }

    private static SessionRepository<? extends Session> asSessionRepository(SessionRepository<?> sessionRepository) {
        return (SessionRepository<? extends Session>) sessionRepository;
    }
}
