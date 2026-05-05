package com.mahitotsu.arachne.samples.delivery.orderservice.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.mahitotsu.arachne.strands.model.Model;

@Configuration
class OrderArachneConfiguration {

    @Bean
    @ConditionalOnProperty(name = "delivery.model.mode", havingValue = "deterministic", matchIfMissing = false)
    Model orderDeterministicModel() {
        return new OrderDeterministicModel();
    }
}