package com.mahitotsu.arachne.samples.streamingsteering;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class StreamingSteeringApplication {

    public static void main(String[] args) {
        SpringApplication.run(StreamingSteeringApplication.class, args);
    }

    @Bean
    DemoStreamingSteeringModel demoStreamingSteeringModel() {
        return new DemoStreamingSteeringModel();
    }
}