package com.mahitotsu.arachne.samples.delivery.idatenadapter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
class IdatenAdapterOpenApiConfiguration {

    @Bean
    OpenAPI idatenAdapterOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Food Delivery Idaten Adapter API")
                .version("v1")
                .description("External low-cost delivery adapter endpoints aligned with food-delivery-demo/docs/apis.md."));
    }
}