package com.mahitotsu.arachne.samples.delivery.registryservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
class RegistryServiceOpenApiConfiguration {

    @Bean
    OpenAPI registryServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Food Delivery Registry Service API")
                .version("v1")
                .description("Capability registration and discovery endpoints aligned with food-delivery-demo/docs/apis.md."));
    }
}