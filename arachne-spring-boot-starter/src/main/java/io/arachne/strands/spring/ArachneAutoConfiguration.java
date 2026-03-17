package io.arachne.strands.spring;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

/**
 * Spring Boot auto-configuration for Arachne Strands.
 *
 * <p>Activated automatically via {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 * Users get an {@code AgentFactory} bean by default; wire in a {@code Model} bean to change provider.
 */
@AutoConfiguration
@EnableConfigurationProperties(ArachneProperties.class)
@ComponentScan(basePackageClasses = ArachneAutoConfiguration.class)
public class ArachneAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AgentFactory agentFactory(ArachneProperties properties) {
        return new AgentFactory(properties);
    }
}
