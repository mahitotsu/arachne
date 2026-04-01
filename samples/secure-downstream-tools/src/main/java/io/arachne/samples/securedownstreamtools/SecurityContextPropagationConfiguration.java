package com.mahitotsu.arachne.samples.securedownstreamtools;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import io.arachne.strands.tool.ExecutionContextPropagation;

@Configuration(proxyBeanMethods = false)
public class SecurityContextPropagationConfiguration {

    @Bean
    ExecutionContextPropagation securityContextPropagation() {
        return task -> {
            SecurityContext captured = SecurityContextHolder.getContext();
            return () -> {
                SecurityContext previous = SecurityContextHolder.getContext();
                SecurityContextHolder.setContext(captured);
                try {
                    task.run();
                } finally {
                    SecurityContextHolder.setContext(previous);
                }
            };
        };
    }
}