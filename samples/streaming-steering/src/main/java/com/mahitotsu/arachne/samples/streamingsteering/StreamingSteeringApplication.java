package com.mahitotsu.arachne.samples.streamingsteering;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class StreamingSteeringApplication {

    public static void main(String[] args) {
        SpringApplication.run(StreamingSteeringApplication.class, args);
    }

    @Bean
    @ConditionalOnProperty(name = "arachne.strands.model.provider", havingValue = "deterministicBuiltIn")
    DemoStreamingSteeringModel demoStreamingSteeringModel() {
        return new DemoStreamingSteeringModel();
    }
}