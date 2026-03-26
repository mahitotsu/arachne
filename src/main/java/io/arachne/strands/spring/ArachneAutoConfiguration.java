package io.arachne.strands.spring;

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

import io.arachne.strands.hooks.HookProvider;
import io.arachne.strands.model.Model;
import io.arachne.strands.model.retry.ExponentialBackoffRetryStrategy;
import io.arachne.strands.model.retry.ModelRetryStrategy;
import io.arachne.strands.session.FileSessionManager;
import io.arachne.strands.session.SessionManager;
import io.arachne.strands.session.SpringSessionManager;
import io.arachne.strands.skills.Skill;
import io.arachne.strands.skills.SkillParser;
import io.arachne.strands.tool.BeanValidationSupport;
import io.arachne.strands.tool.ExecutionContextPropagation;
import io.arachne.strands.tool.builtin.BuiltInResourceAccessPolicy;
import io.arachne.strands.tool.builtin.CurrentTimeTool;
import io.arachne.strands.tool.builtin.ResourceListTool;
import io.arachne.strands.tool.builtin.ResourceReaderTool;
import io.arachne.strands.tool.annotation.DiscoveredTool;
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
    public io.arachne.strands.tool.annotation.AnnotationToolScanner annotationToolScanner(
            ObjectMapper objectMapper,
            Validator validator) {
        return new io.arachne.strands.tool.annotation.AnnotationToolScanner(
                new io.arachne.strands.schema.JsonSchemaGenerator(objectMapper),
                objectMapper,
                validator);
    }

    @Bean(name = "arachneDiscoveredTools")
    @ConditionalOnMissingBean(name = "arachneDiscoveredTools")
    public List<DiscoveredTool> arachneDiscoveredTools(ApplicationContext applicationContext,
                                             io.arachne.strands.tool.annotation.AnnotationToolScanner annotationToolScanner) {
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
