package io.arachne.strands.spring;

import java.util.List;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import jakarta.validation.Validator;

import io.arachne.strands.model.Model;
import io.arachne.strands.tool.BeanValidationSupport;
import io.arachne.strands.tool.annotation.DiscoveredTool;

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
    public AgentFactory agentFactory(
            ArachneProperties properties,
            Model model,
            List<DiscoveredTool> arachneDiscoveredTools,
            Validator validator) {
        return new AgentFactory(properties, model, arachneDiscoveredTools, validator);
    }
}
