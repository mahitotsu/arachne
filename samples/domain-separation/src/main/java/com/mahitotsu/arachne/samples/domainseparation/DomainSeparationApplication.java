package com.mahitotsu.arachne.samples.domainseparation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.mahitotsu.arachne.samples.domainseparation.config.DomainSeparationProperties;

@SpringBootApplication
@EnableConfigurationProperties(DomainSeparationProperties.class)
public class DomainSeparationApplication {

    public static void main(String[] args) {
        SpringApplication.run(DomainSeparationApplication.class, args);
    }
}