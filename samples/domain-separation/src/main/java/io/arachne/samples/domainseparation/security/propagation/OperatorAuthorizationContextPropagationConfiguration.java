package io.arachne.samples.domainseparation.security.propagation;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.arachne.samples.domainseparation.security.OperatorAuthorizationContext;
import io.arachne.samples.domainseparation.security.OperatorAuthorizationContextHolder;
import io.arachne.strands.tool.ExecutionContextPropagation;

@Configuration(proxyBeanMethods = false)
public class OperatorAuthorizationContextPropagationConfiguration {

    @Bean
    ExecutionContextPropagation operatorAuthorizationContextPropagation(
            OperatorAuthorizationContextHolder authorizationContextHolder) {
        return task -> {
            OperatorAuthorizationContext captured = authorizationContextHolder.current();
            return () -> {
                OperatorAuthorizationContext previous = authorizationContextHolder.current();
                authorizationContextHolder.restore(captured);
                try {
                    task.run();
                } finally {
                    authorizationContextHolder.restore(previous);
                }
            };
        };
    }
}