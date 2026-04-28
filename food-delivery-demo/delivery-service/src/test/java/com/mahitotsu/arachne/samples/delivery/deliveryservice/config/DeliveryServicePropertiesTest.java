package com.mahitotsu.arachne.samples.delivery.deliveryservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class DeliveryServicePropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    ValidationAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void bindsDeliverySettings() {
        contextRunner
                .withPropertyValues(
                        "delivery.registry.base-url=http://registry-service:8087",
                        "delivery.delivery.endpoint=http://delivery-service:8080",
                        "delivery.model.mode=deterministic")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    DeliveryServiceProperties properties = context.getBean(DeliveryServiceProperties.class);
                    assertThat(properties.getRegistry().getBaseUrl()).isEqualTo("http://registry-service:8087");
                    assertThat(properties.getDelivery().getEndpoint()).isEqualTo("http://delivery-service:8080");
                    assertThat(properties.getModel().getMode()).isEqualTo("deterministic");
                });
    }

    @Test
    void rejectsUnknownModelMode() {
        contextRunner
                .withPropertyValues("delivery.model.mode=chaos")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("delivery.model.mode must be live or deterministic");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(DeliveryServiceProperties.class)
    static class TestConfiguration {
    }
}