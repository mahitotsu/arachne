package io.arachne.samples.phase6streamingsteering;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Phase6StreamingSteeringApplication {

    public static void main(String[] args) {
        SpringApplication.run(Phase6StreamingSteeringApplication.class, args);
    }

    @Bean
    DemoStreamingSteeringModel demoStreamingSteeringModel() {
        return new DemoStreamingSteeringModel();
    }
}