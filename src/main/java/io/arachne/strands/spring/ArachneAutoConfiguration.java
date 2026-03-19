package io.arachne.strands.spring;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.session.MapSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

import io.arachne.strands.model.Model;
import io.arachne.strands.model.retry.ExponentialBackoffRetryStrategy;
import io.arachne.strands.model.retry.ModelRetryStrategy;
import io.arachne.strands.session.FileSessionManager;
import io.arachne.strands.session.SessionManager;
import io.arachne.strands.session.SpringSessionManager;
import io.arachne.strands.tool.BeanValidationSupport;
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
    public io.arachne.strands.tool.annotation.AnnotationToolScanner annotationToolScanner(Validator validator) {
        return new io.arachne.strands.tool.annotation.AnnotationToolScanner(
                new io.arachne.strands.schema.JsonSchemaGenerator(),
                validator);
    }

    @Bean(name = "arachneDiscoveredTools")
    @ConditionalOnMissingBean(name = "arachneDiscoveredTools")
    public List<DiscoveredTool> arachneDiscoveredTools(ApplicationContext applicationContext,
                                             io.arachne.strands.tool.annotation.AnnotationToolScanner annotationToolScanner) {
        return annotationToolScanner.scanDiscoveredTools(applicationContext.getBeansOfType(Object.class).values());
    }

    @Bean
    @ConditionalOnMissingBean
    public SessionManager sessionManager(
            ArachneProperties properties,
            ObjectProvider<SessionRepository<?>> sessionRepositoryProvider) {
        String directory = properties.getAgent().getSession().getFile().getDirectory();
        if (directory != null && !directory.isBlank()) {
            return new FileSessionManager(Path.of(directory));
        }
        SessionRepository<?> sessionRepository = sessionRepositoryProvider.getIfAvailable();
        if (sessionRepository != null) {
            return new SpringSessionManager(asSessionRepository(sessionRepository));
        }
        return new SpringSessionManager(new MapSessionRepository(new ConcurrentHashMap<>()));
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
            List<DiscoveredTool> arachneDiscoveredTools,
            Validator validator,
            SessionManager sessionManager,
            ObjectProvider<ModelRetryStrategy> modelRetryStrategyProvider) {
        return new AgentFactory(
                properties,
                model,
                arachneDiscoveredTools,
                validator,
                sessionManager,
                modelRetryStrategyProvider.getIfAvailable());
    }

    private static SessionRepository<? extends Session> asSessionRepository(SessionRepository<?> sessionRepository) {
        return (SessionRepository<? extends Session>) sessionRepository;
    }
}
