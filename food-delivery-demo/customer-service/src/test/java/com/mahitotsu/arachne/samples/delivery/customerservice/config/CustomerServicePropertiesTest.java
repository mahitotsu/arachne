package com.mahitotsu.arachne.samples.delivery.customerservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class CustomerServicePropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    ValidationAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void bindsCustomerSettings() {
        contextRunner
                .withPropertyValues(
                        "delivery.registry.base-url=http://registry-service:8087",
                        "delivery.customer.endpoint=http://customer-service:8080",
                        "delivery.auth.issuer=http://customer-service:8080")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    CustomerServiceProperties properties = context.getBean(CustomerServiceProperties.class);
                    assertThat(properties.getRegistry().getBaseUrl()).isEqualTo("http://registry-service:8087");
                    assertThat(properties.getCustomer().getEndpoint()).isEqualTo("http://customer-service:8080");
                    assertThat(properties.getAuth().getIssuer()).isEqualTo("http://customer-service:8080");
                });
    }

    @Test
    void rejectsInvalidIssuer() {
        contextRunner
                .withPropertyValues("delivery.auth.issuer=issuer")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("delivery.auth.issuer must be an absolute http(s) URL");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CustomerServiceProperties.class)
    static class TestConfiguration {
    }
}