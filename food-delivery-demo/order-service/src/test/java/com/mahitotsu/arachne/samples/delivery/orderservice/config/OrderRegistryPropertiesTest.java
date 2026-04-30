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
        void bindsDeliveryRegistryAndCapabilityQuerySettings() {
        contextRunner
                .withPropertyValues(
                        "delivery.registry.base-url=http://registry-service:8087",
                                                "delivery.order.endpoint=http://order-service:8080",
                                                "delivery.downstream.menu.capability-query=メニュー提案 在庫付き提案 注文候補",
                                                "delivery.downstream.delivery.capability-query=配送候補 ETA 比較 配送選択肢",
                                                "delivery.downstream.payment.capability-query=支払い準備 合計確認 課金確定",
                                                "delivery.downstream.support.capability-query=注文後フィードバック受付 問い合わせ受付 サポート連携")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(OrderRegistryProperties.class);

                    OrderRegistryProperties properties = context.getBean(OrderRegistryProperties.class);
                    assertThat(properties.getRegistry().getBaseUrl()).isEqualTo("http://registry-service:8087");
                    assertThat(properties.getOrder().getEndpoint()).isEqualTo("http://order-service:8080");
                                        assertThat(properties.getDownstream().getMenu().getCapabilityQuery()).isEqualTo("メニュー提案 在庫付き提案 注文候補");
                                        assertThat(properties.getDownstream().getDelivery().getCapabilityQuery()).isEqualTo("配送候補 ETA 比較 配送選択肢");
                                        assertThat(properties.getDownstream().getPayment().getCapabilityQuery()).isEqualTo("支払い準備 合計確認 課金確定");
                                        assertThat(properties.getDownstream().getSupport().getCapabilityQuery()).isEqualTo("注文後フィードバック受付 問い合わせ受付 サポート連携");
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