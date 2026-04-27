package com.mahitotsu.arachne.samples.delivery.orderservice;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OrderConfiguration {

    @Bean
    ApplicationRunner seedHistoricalOrder(OrderRepository orderRepository) {
        return args -> {
            if (orderRepository.findLatestOrderForUser("cust-demo-001").isEmpty()) {
                orderRepository.saveConfirmedOrder(
                        "cust-demo-001",
                        List.of(
                                new OrderLineItem("Crispy Chicken Box", 2, new BigDecimal("980.00"), "初期データ"),
                                new OrderLineItem("Curly Fries", 1, new BigDecimal("330.00"), "初期データ"),
                                new OrderLineItem("Lemon Soda", 2, new BigDecimal("240.00"), "初期データ")),
                        new BigDecimal("2530.00"),
                        new BigDecimal("2830.00"),
                        "18 分・自社エクスプレス",
                        "CHARGED");
            }
        };
    }
}