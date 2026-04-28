package com.mahitotsu.arachne.samples.delivery.hermesadapter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
class HermesAdapterOpenApiConfiguration {

    @Bean
    OpenAPI hermesAdapterOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Food Delivery Hermes Adapter API")
                .version("v1")
                .description("food-delivery-demo/docs/apis.md に対応した外部高速配送アダプターのエンドポイントです。"));
    }
}