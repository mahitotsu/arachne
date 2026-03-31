package io.arachne.samples.marketplace.caseservice;

import java.time.Clock;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Configuration
@EnableConfigurationProperties(CaseServiceConfiguration.WorkflowServiceProperties.class)
class CaseServiceConfiguration {

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
    RestClient workflowServiceRestClient(RestClient.Builder builder, WorkflowServiceProperties properties) {
        return builder.baseUrl(properties.baseUrl()).build();
    }

    @Bean
    CorsFilter caseServiceCorsFilter() {
        var configuration = new CorsConfiguration();
        configuration.addAllowedOrigin("http://localhost:3000");
        configuration.addAllowedMethod("GET");
        configuration.addAllowedMethod("POST");
        configuration.addAllowedMethod("OPTIONS");
        configuration.addAllowedHeader(CorsConfiguration.ALL);

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return new CorsFilter(source);
    }

    @ConfigurationProperties(prefix = "workflow-service")
    record WorkflowServiceProperties(String baseUrl) {
    }
}