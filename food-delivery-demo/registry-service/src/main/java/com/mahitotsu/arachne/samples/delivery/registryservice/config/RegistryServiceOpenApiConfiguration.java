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
                .description("food-delivery-demo/docs/apis.md に対応した capability 登録と discovery のエンドポイントです。"));
    }
}