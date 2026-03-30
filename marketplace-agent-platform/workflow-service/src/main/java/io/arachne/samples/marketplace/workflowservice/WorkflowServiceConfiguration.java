package io.arachne.samples.marketplace.workflowservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.time.Clock;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

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