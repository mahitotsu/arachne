package com.mahitotsu.arachne.samples.delivery.orderservice.config;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class OrderRegistryPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    ValidationAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void bindsDeliveryRegistrySettings() {
        contextRunner
                .withPropertyValues(
                        "delivery.registry.base-url=http://registry-service:8087",
                                                "delivery.order.endpoint=http://order-service:8080",
                                                "delivery.downstream.menu.service-name=legacy-menu-service",
                                                "delivery.downstream.delivery.service-name=legacy-delivery-service",
                                                "delivery.downstream.payment.service-name=legacy-payment-service",
                                                "delivery.downstream.support.service-name=legacy-support-service")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(OrderRegistryProperties.class);

                    OrderRegistryProperties properties = context.getBean(OrderRegistryProperties.class);
                    assertThat(properties.getRegistry().getBaseUrl()).isEqualTo("http://registry-service:8087");
                    assertThat(properties.getOrder().getEndpoint()).isEqualTo("http://order-service:8080");
                                        assertThat(properties.getDownstream().getMenu().getServiceName()).isEqualTo("legacy-menu-service");
                                        assertThat(properties.getDownstream().getDelivery().getServiceName()).isEqualTo("legacy-delivery-service");
                                        assertThat(properties.getDownstream().getPayment().getServiceName()).isEqualTo("legacy-payment-service");
                                        assertThat(properties.getDownstream().getSupport().getServiceName()).isEqualTo("legacy-support-service");
                });
    }

    @Test
    void rejectsInvalidOrderEndpoint() {
        contextRunner
                .withPropertyValues("delivery.order.endpoint=not-a-url")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                                                        .hasStackTraceContaining("delivery.order.endpoint must be an absolute http(s) URL");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(OrderRegistryProperties.class)
    static class TestConfiguration {
    }
}